package stillframe42.aicodereviewer.agent.adapter.out.remote

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.client.WebClientResponseException
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// RemoteAgentClient 의 HTTP 예외 매핑 회귀 안전망.
// AbstractIntegrationTest 의 공유 WireMock 으로 agent.remote.url 이 라우팅되어 있으므로
// 별도 컨텍스트를 만들지 않고 클라이언트 빈을 그대로 주입받는다.
class RemoteAgentClientTest : AbstractIntegrationTest() {

    companion object {
        // X-Internal-Auth 헤더 검증용 — base 의 @DynamicPropertySource 는 @TestPropertySource 를
        // 덮어써 다른 테스트(RagContext 등)와 충돌하므로, 이 클래스 전용 컨텍스트에만 주입한다
        @JvmStatic
        @DynamicPropertySource
        fun overrideAuthToken(registry: DynamicPropertyRegistry) {
            registry.add("agent.remote.callback.internal-auth-token") { "test-internal-token" }
        }
    }

    @Autowired
    private lateinit var remoteAgentClient: RemoteAgentClient

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
            runBlocking { remoteAgentClient.requestDeepAnalysis(sampleCommand) }
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
            runBlocking { remoteAgentClient.requestDeepAnalysis(sampleCommand) }
        }
    }

    @Test
    fun `getAnalysisResult 가 5xx 응답을 받으면 AgentUnavailableException 으로 매핑된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/agent/analyze/.+"))
                .willReturn(aResponse().withStatus(500)),
        )

        val ex = assertThrows<AgentUnavailableException> {
            runBlocking { remoteAgentClient.getAnalysisResult("abc-123") }
        }
        assertThat(ex.message).contains("500")
    }

    // ai-agent-service 가 /agent 라우터 전체에 X-Internal-Auth 인증을 요구하므로
    // 모든 요청에 공유 비밀 헤더가 실려야 한다 (누락 시 401)
    @Test
    fun `requestDeepAnalysis 요청에 X-Internal-Auth 헤더가 실린다`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"analysis_id":"a-1","status":"PROCESSING","issues":[]}"""),
                ),
        )

        runBlocking { remoteAgentClient.requestDeepAnalysis(sampleCommand) }

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/agent/analyze"))
                .withHeader("X-Internal-Auth", equalTo("test-internal-token")),
        )
    }

    @Test
    fun `getAnalysisResult 요청에 X-Internal-Auth 헤더가 실린다`() {
        wireMock.stubFor(
            get(urlPathMatching("/agent/analyze/.+"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"analysis_id":"abc-123","status":"DONE","issues":[]}"""),
                ),
        )

        runBlocking { remoteAgentClient.getAnalysisResult("abc-123") }

        wireMock.verify(
            getRequestedFor(urlPathMatching("/agent/analyze/.+"))
                .withHeader("X-Internal-Auth", equalTo("test-internal-token")),
        )
    }
}
