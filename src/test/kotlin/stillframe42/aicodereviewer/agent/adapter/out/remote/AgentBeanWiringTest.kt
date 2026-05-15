package stillframe42.aicodereviewer.agent.adapter.out.remote

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs

class AgentBeanWiringTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var agentPort: AgentAnalysisPort

    @Test
    fun `AgentAnalysisPort 빈이 RemoteAgentClient 로 주입된다`() {
        assertThat(agentPort).isInstanceOf(RemoteAgentClient::class.java)
    }

    @Test
    fun `remoteAgentWebClient 가 주입되고 WireMock 응답을 도메인 결과로 매핑한다`() = runTest {
        WireMockStubs.stubRemoteAgentAnalyze(wireMock)

        val result = agentPort.requestDeepAnalysis(
            AgentAnalysisCommand(
                prNumber = 99,
                repo = "owner/repo",
                diff = "diff",
            ),
        )

        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.analysisId).isEqualTo("agent-analysis-stub")
        assertThat(result.findings).hasSize(1)
        assertThat(result.findings[0].description).isEqualTo("라우팅 회귀 검증 finding")
    }
}
