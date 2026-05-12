package stillframe42.aicodereviewer.agent.application

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisFailedException
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisTimeoutException
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.config.PythonAgentProperties
import java.io.IOException
import java.time.Duration

class AgentPollerTest {

    private fun props(
        maxAttempts: Int = 3,
        interval: Duration = Duration.ofMillis(10),
        timeout: Duration = Duration.ofSeconds(1),
    ) = PythonAgentProperties(
        url = "http://test",
        connectTimeout = Duration.ofSeconds(1),
        readTimeout = Duration.ofSeconds(1),
        maxInMemorySize = DataSize.ofMegabytes(1),
        poll = PythonAgentProperties.PollProperties(maxAttempts, interval, timeout),
    )

    private class FakeAgentAnalysisPort(
        private val responses: List<AgentAnalysisResult>,
        private val ioExceptionAfter: Int = -1,
    ) : AgentAnalysisPort {
        var calls = 0
            private set

        override suspend fun requestDeepAnalysis(command: AgentAnalysisCommand): AgentAnalysisResult =
            error("not used in poller test")

        override suspend fun getAnalysisResult(analysisId: String): AgentAnalysisResult {
            if (calls == ioExceptionAfter) throw IOException("simulated IO failure")
            return responses[calls++.coerceAtMost(responses.lastIndex)]
        }

        override suspend fun checkHealth() = true
    }

    private fun result(status: String, error: String? = null) =
        AgentAnalysisResult(analysisId = "id-1", status = status, findings = emptyList(), error = error)

    @Test
    fun `IN_PROGRESS 후 DONE 시퀀스에서 결과 반환`() = runTest {
        val port = FakeAgentAnalysisPort(listOf(result("PROCESSING"), result("DONE")))
        val poller = AgentPoller(port, props(maxAttempts = 5))

        val r = poller.pollUntilComplete("id-1")

        assertThat(r.status).isEqualTo("DONE")
        assertThat(port.calls).isEqualTo(2)
    }

    @Test
    fun `maxAttempts 모두 PROCESSING 이면 max attempts 메시지로 timeout`() = runTest {
        val port = FakeAgentAnalysisPort(listOf(result("PROCESSING")))
        val poller = AgentPoller(
            port,
            props(maxAttempts = 3, interval = Duration.ofMillis(10), timeout = Duration.ofSeconds(1)),
        )

        val ex = runCatching { poller.pollUntilComplete("id-1") }.exceptionOrNull()

        assertThat(ex).isInstanceOf(AgentAnalysisTimeoutException::class.java)
        assertThat(ex).hasMessageContaining("max attempts 3 reached")
        assertThat(port.calls).isEqualTo(3)
    }

    @Test
    fun `wall-clock timeout 이 먼저 도달하면 wall-clock 메시지로 timeout`() = runTest {
        val port = FakeAgentAnalysisPort(listOf(result("PROCESSING")))
        val poller = AgentPoller(
            port,
            props(maxAttempts = 100, interval = Duration.ofMillis(200), timeout = Duration.ofMillis(50)),
        )

        val ex = runCatching { poller.pollUntilComplete("id-1") }.exceptionOrNull()

        assertThat(ex).isInstanceOf(AgentAnalysisTimeoutException::class.java)
        assertThat(ex).hasMessageContaining("wall-clock timeout")
    }

    @Test
    fun `FAILED 즉시 AgentAnalysisFailedException`() = runTest {
        val port = FakeAgentAnalysisPort(listOf(result("FAILED", error = "분석 중단")))
        val poller = AgentPoller(port, props())

        val ex = runCatching { poller.pollUntilComplete("id-1") }.exceptionOrNull()

        assertThat(ex).isInstanceOf(AgentAnalysisFailedException::class.java)
        assertThat(ex).hasMessageContaining("분석 중단")
        assertThat(port.calls).isEqualTo(1)
    }

    @Test
    fun `port 의 IOException 은 catch 통과 후 호출자에게 전파`() = runTest {
        val port = FakeAgentAnalysisPort(listOf(result("PROCESSING")), ioExceptionAfter = 1)
        val poller = AgentPoller(port, props(maxAttempts = 5))

        val ex = runCatching { poller.pollUntilComplete("id-1") }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IOException::class.java)
        assertThat(ex).hasMessageContaining("simulated IO failure")
    }
}
