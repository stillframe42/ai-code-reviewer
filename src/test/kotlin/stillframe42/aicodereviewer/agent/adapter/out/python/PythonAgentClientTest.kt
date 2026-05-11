package stillframe42.aicodereviewer.agent.adapter.out.python

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult

class PythonAgentClientTest {

    private val client = PythonAgentClient(agentUrl = "http://test")

    @Test
    fun `requestDeepAnalysis 가 stub-prefix analysisId 와 INFO STUB finding 1건을 가진 결과를 반환한다`() = runTest {
        val command = AgentAnalysisCommand(
            prNumber = 7,
            repo = "owner/repo",
            diff = "diff --git a/foo b/foo",
        )

        val result: AgentAnalysisResult = client.requestDeepAnalysis(command)

        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.analysisId).startsWith("stub-")
        assertThat(result.error).isNull()
        assertThat(result.findings).hasSize(1)

        val finding = result.findings[0]
        assertThat(finding.severity).isEqualTo("INFO")
        assertThat(finding.type).isEqualTo("STUB")
        assertThat(finding.location).contains("7")
        assertThat(finding.description).isEqualTo("Python 에이전트 연동 전 임시 응답")
        assertThat(finding.suggestion).isEqualTo("DAY 15 이후 실제 에이전트 연동 예정")
        assertThat(finding.owaspReference).isNull()
    }

    @Test
    fun `getAnalysisResult 가 전달된 analysisId 와 DONE 상태로 빈 findings 결과를 반환한다`() = runTest {
        val result = client.getAnalysisResult("foo")

        assertThat(result.analysisId).isEqualTo("foo")
        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.findings).isEmpty()
        assertThat(result.error).isNull()
    }

    @Test
    fun `checkHealth 는 항상 true 를 반환한다`() = runTest {
        assertThat(client.checkHealth()).isTrue()
    }

}
