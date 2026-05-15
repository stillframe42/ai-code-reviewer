package stillframe42.aicodereviewer.agent.application

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisFailedException
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisTimeoutException
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs

class AgentPollerIntegrationTest : AbstractIntegrationTest() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun pollOverrides(registry: DynamicPropertyRegistry) {
            // 통합 테스트 시간 단축 — interval/timeout 매우 짧게
            registry.add("agent.python.poll.interval") { "10ms" }
            registry.add("agent.python.poll.timeout") { "5s" }
            registry.add("agent.python.poll.max-attempts") { "30" }
        }
    }

    @Autowired
    private lateinit var poller: AgentPoller

    @Test
    fun `PROCESSING PROCESSING DONE 시퀀스에서 3번째 polling 으로 결과 반환`(): Unit = runBlocking {
        val analysisId = "int-test-1"
        WireMockStubs.stubAgentPollSequence(
            wireMock = wireMock,
            analysisId = analysisId,
            statuses = listOf("PROCESSING", "PROCESSING", "DONE"),
            finalIssuesJson = """[{"severity":"HIGH","type":"X","location":"a:1","description":"d","suggestion":"s","owasp_reference":"A01:2021"}]""",
        )

        val result = poller.pollUntilComplete(analysisId)

        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.findings).hasSize(1)
    }

    @Test
    fun `FAILED 응답 즉시 AgentAnalysisFailedException`(): Unit = runBlocking {
        val analysisId = "int-test-2"
        WireMockStubs.stubAgentPollSequence(
            wireMock = wireMock,
            analysisId = analysisId,
            statuses = listOf("FAILED"),
            finalError = "model timeout",
        )

        val ex = runCatching { poller.pollUntilComplete(analysisId) }.exceptionOrNull()

        assertThat(ex).isInstanceOf(AgentAnalysisFailedException::class.java)
        assertThat(ex).hasMessageContaining("model timeout")
    }

    @Test
    fun `모든 응답이 PROCESSING 인 경우 timeout 발생`(): Unit = runBlocking {
        val analysisId = "int-test-3"
        // 30회 모두 PROCESSING — interval 10ms × 30 = 300ms 안에 max-attempts 도달
        WireMockStubs.stubAgentPollSequence(
            wireMock = wireMock,
            analysisId = analysisId,
            statuses = List(30) { "PROCESSING" },
        )

        val ex = runCatching { poller.pollUntilComplete(analysisId) }.exceptionOrNull()

        assertThat(ex).isInstanceOf(AgentAnalysisTimeoutException::class.java)
        assertThat(ex).hasMessageContaining("max attempts 30")
    }

    @Test
    fun `5xx 응답은 AgentUnavailableException 으로 매핑되어 전파된다`(): Unit = runBlocking {
        val analysisId = "int-test-4"
        wireMock.stubFor(
            get(urlPathEqualTo("/agent/analyze/$analysisId"))
                .willReturn(aResponse().withStatus(503)),
        )

        val ex = runCatching { poller.pollUntilComplete(analysisId) }.exceptionOrNull()

        assertThat(ex).isInstanceOf(AgentUnavailableException::class.java)
        assertThat(ex).hasMessageContaining("503")
    }
}
