package stillframe42.aicodereviewer.agent.adapter.out.python

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// PythonAgentClient 의 HTTP 예외 매핑 회귀 안전망.
// AbstractIntegrationTest 의 공유 WireMock 으로 agent.python.url 이 라우팅되어 있으므로
// 별도 컨텍스트를 만들지 않고 클라이언트 빈을 그대로 주입받는다.
class PythonAgentClientTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var pythonAgentClient: PythonAgentClient

    private val sampleCommand = AgentAnalysisCommand(
        prNumber = 1,
        repo = "owner/repo",
        diff = "diff --git a/SecurityConfig.kt b/SecurityConfig.kt\n+token = \"x\"\n",
        contextIds = emptyList(),
        analysisType = "SECURITY",
    )

    @Test
    fun `requestDeepAnalysis 가 5xx 응답을 받으면 AgentUnavailableException 으로 매핑된다`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(aResponse().withStatus(503)),
        )

        val ex = assertThrows<AgentUnavailableException> {
            runBlocking { pythonAgentClient.requestDeepAnalysis(sampleCommand) }
        }
        assertThat(ex.message).contains("503")
        assertThat(ex.cause).isInstanceOf(WebClientResponseException::class.java)
    }

    @Test
    fun `requestDeepAnalysis 가 4xx 응답을 받으면 WebClientResponseException 가 그대로 전파된다`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(aResponse().withStatus(400)),
        )

        assertThrows<WebClientResponseException> {
            runBlocking { pythonAgentClient.requestDeepAnalysis(sampleCommand) }
        }
    }

    @Test
    fun `getAnalysisResult 가 5xx 응답을 받으면 AgentUnavailableException 으로 매핑된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/agent/analyze/.+"))
                .willReturn(aResponse().withStatus(500)),
        )

        val ex = assertThrows<AgentUnavailableException> {
            runBlocking { pythonAgentClient.getAnalysisResult("abc-123") }
        }
        assertThat(ex.message).contains("500")
    }
}
