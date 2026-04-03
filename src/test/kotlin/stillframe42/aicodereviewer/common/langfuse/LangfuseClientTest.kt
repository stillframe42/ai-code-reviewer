package stillframe42.aicodereviewer.common.langfuse

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import java.util.Base64
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import stillframe42.aicodereviewer.config.LangfuseProperties
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// LangfuseClient 통합 테스트 — WireMock으로 Langfuse API 서버를 모킹
class LangfuseClientTest : AbstractIntegrationTest() {

    private fun buildClient(): LangfuseClient {
        // WireMock 주소를 host로 사용하는 클라이언트 생성
        val testProperties = LangfuseProperties(
            host = "http://localhost:${wireMock.port()}",
            secretKey = "test-secret",
            publicKey = "test-public",
            enabled = true,
        )
        return LangfuseClient(testProperties)
    }

    @Test
    fun `ingestion 요청이 올바른 Basic Auth 헤더와 함께 전송된다`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/public/ingestion"))
                .willReturn(
                    aResponse()
                        .withStatus(207)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"successes":[],"errors":[]}""")
                )
        )

        val client = buildClient()
        client.ingest(listOf(
            mapOf("type" to "trace-create", "id" to "evt-1", "body" to mapOf("id" to "trace-1", "name" to "test"))
        ))

        val expectedAuth = Base64.getEncoder().encodeToString("test-public:test-secret".toByteArray())
        wireMock.verify(
            postRequestedFor(urlEqualTo("/api/public/ingestion"))
                .withHeader("Authorization", equalTo("Basic $expectedAuth"))
                .withHeader("Content-Type", equalTo("application/json"))
        )
    }

    @Test
    fun `ingestion 실패 시 예외를 throw한다`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/public/ingestion"))
                .willReturn(aResponse().withStatus(500))
        )

        val client = buildClient()
        assertThrows<RuntimeException> {
            client.ingest(listOf(
                mapOf("type" to "trace-create", "id" to "evt-1", "body" to mapOf("id" to "trace-1"))
            ))
        }
    }
}
