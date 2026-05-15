package stillframe42.aicodereviewer.agent.adapter.out.remote

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

class RemoteAgentClientIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var remoteAgentClient: RemoteAgentClient

    @Test
    fun `requestDeepAnalysis - 200 응답을 도메인 결과로 매핑한다`() = runTest {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(
                    okJson(
                        """
                        {
                          "analysis_id": "id-001",
                          "status": "DONE",
                          "issues": [
                            {
                              "severity": "HIGH",
                              "type": "SECURITY",
                              "location": "SecurityConfig.kt:42",
                              "description": "plaintext password",
                              "suggestion": "use BCrypt",
                              "owasp_reference": "A02:2021"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
                ),
        )

        val result = remoteAgentClient.requestDeepAnalysis(
            AgentAnalysisCommand(prNumber = 1, repo = "owner/repo", diff = "diff"),
        )

        assertThat(result.analysisId).isEqualTo("id-001")
        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.findings).hasSize(1)
        assertThat(result.findings[0].owaspReference).isEqualTo("A02:2021")
    }

    @Test
    fun `requestDeepAnalysis - 요청 바디가 snake_case 로 직렬화된다`() = runTest {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(okJson("""{"analysis_id":"x","status":"DONE","issues":[]}""")),
        )

        remoteAgentClient.requestDeepAnalysis(
            AgentAnalysisCommand(
                prNumber = 7,
                repo = "owner/repo",
                diff = "d",
                contextIds = listOf("ctx-1"),
                analysisType = "SECURITY",
                sessionId = "sess-1",
            ),
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/agent/analyze"))
                .withRequestBody(
                    equalToJson(
                        """
                        {
                          "pr_number": 7,
                          "repo": "owner/repo",
                          "diff": "d",
                          "context_ids": ["ctx-1"],
                          "analysis_type": "SECURITY",
                          "session_id": "sess-1"
                        }
                        """.trimIndent(),
                    ),
                ),
        )
    }

    @Test
    fun `getAnalysisResult - IN_PROGRESS 응답을 그대로 반환한다 (폴링 없음)`() = runTest {
        wireMock.stubFor(
            get(urlPathEqualTo("/agent/analyze/id-002"))
                .willReturn(okJson("""{"analysis_id":"id-002","status":"IN_PROGRESS","issues":[]}""")),
        )

        val result = remoteAgentClient.getAnalysisResult("id-002")

        assertThat(result.analysisId).isEqualTo("id-002")
        assertThat(result.status).isEqualTo("IN_PROGRESS")
        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `getAnalysisResult - DONE 응답에서 findings 를 매핑한다`() = runTest {
        wireMock.stubFor(
            get(urlPathEqualTo("/agent/analyze/id-003"))
                .willReturn(
                    okJson(
                        """
                        {
                          "analysis_id": "id-003",
                          "status": "DONE",
                          "issues": [
                            {
                              "severity": "MEDIUM",
                              "type": "STYLE",
                              "location": "Foo.kt:1",
                              "description": "...",
                              "suggestion": "..."
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
                ),
        )

        val result = remoteAgentClient.getAnalysisResult("id-003")

        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.findings).hasSize(1)
        assertThat(result.findings[0].severity).isEqualTo("MEDIUM")
    }

    @Test
    fun `checkHealth - 200 응답시 true 를 반환한다`() = runTest {
        wireMock.stubFor(
            get(urlPathEqualTo("/health")).willReturn(aResponse().withStatus(200)),
        )

        assertThat(remoteAgentClient.checkHealth()).isTrue()
    }

    @Test
    fun `checkHealth - 5xx 응답시 false 를 반환한다`() = runTest {
        wireMock.stubFor(
            get(urlPathEqualTo("/health")).willReturn(aResponse().withStatus(503)),
        )

        assertThat(remoteAgentClient.checkHealth()).isFalse()
    }

    @Test
    fun `checkHealth - stub 이 없으면 (404) false 를 반환한다`() = runTest {
        // WireMock 은 매칭되는 stub 이 없으면 404 — 어댑터가 false 로 처리해야 한다
        assertThat(remoteAgentClient.checkHealth()).isFalse()
    }
}
