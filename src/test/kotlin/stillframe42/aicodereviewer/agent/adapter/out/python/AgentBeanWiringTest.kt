package stillframe42.aicodereviewer.agent.adapter.out.python

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

class AgentBeanWiringTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var agentPort: AgentAnalysisPort

    @Test
    fun `AgentAnalysisPort 빈이 PythonAgentClient 로 주입된다`() {
        assertThat(agentPort).isInstanceOf(PythonAgentClient::class.java)
    }

    @Test
    fun `application-agent_yml 프로퍼티 로딩 후 스텁 호출이 정상 동작한다`() = runTest {
        val result = agentPort.requestDeepAnalysis(
            AgentAnalysisCommand(
                prNumber = 99,
                repo = "owner/repo",
                diff = "diff",
            ),
        )

        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.analysisId).startsWith("stub-")
        assertThat(result.findings).hasSize(1)
    }
}
