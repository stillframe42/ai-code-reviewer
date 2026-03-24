package stillframe42.aicodereviewer.github.adapter.`in`.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import stillframe42.aicodereviewer.config.GitHubProperties

// WebhookController 통합 테스트 — RANDOM_PORT 실제 서버에 RestTestClient로 검증합니다.
// fire-and-forget 방식이므로 202 이후의 백그라운드 처리는 검증하지 않습니다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhookControllerTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var properties: GitHubProperties

    private lateinit var client: RestTestClient

    @BeforeEach
    fun setUp() {
        client = RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }

    // 설정에서 주입받은 secret으로 서명을 계산해 설정값 변경에도 테스트가 깨지지 않도록 한다
    private fun sign(payload: String): String =
        "sha256=${HmacSignatureVerifier.computeSignature(payload.toByteArray(Charsets.UTF_8), properties.app.webhookSecret)}"

    private val pullRequestPayload = """
        {
          "action": "opened",
          "installation": { "id": 12345678 },
          "repository": { "full_name": "owner/repo" },
          "pull_request": {
            "number": 42,
            "head": { "sha": "abc123def456" }
          }
        }
    """.trimIndent()

    @Test
    fun `X-Hub-Signature-256 헤더가 없으면 401 Unauthorized 반환`() {
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-GitHub-Event", "pull_request")
            .body(pullRequestPayload)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `잘못된 서명이면 401 Unauthorized 반환`() {
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", "sha256=invalidsignature")
            .header("X-GitHub-Event", "pull_request")
            .body(pullRequestPayload)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `올바른 서명의 ping 이벤트는 200 OK 반환`() {
        val payload = """{"zen":"Keep it logically awesome.","hook_id":123}"""
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(payload))
            .header("X-GitHub-Event", "ping")
            .body(payload)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `올바른 서명의 pull_request opened 이벤트는 202 Accepted 반환`() {
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(pullRequestPayload))
            .header("X-GitHub-Event", "pull_request")
            .body(pullRequestPayload)
            .exchange()
            .expectStatus().isEqualTo(202)
    }

    @Test
    fun `올바른 서명이지만 지원하지 않는 PR action은 200 OK 반환`() {
        val closedPayload = pullRequestPayload.replace("\"opened\"", "\"closed\"")
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(closedPayload))
            .header("X-GitHub-Event", "pull_request")
            .body(closedPayload)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `올바른 서명의 pull_request synchronize 이벤트는 202 Accepted 반환`() {
        val synchronizePayload = pullRequestPayload.replace("\"opened\"", "\"synchronize\"")
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(synchronizePayload))
            .header("X-GitHub-Event", "pull_request")
            .body(synchronizePayload)
            .exchange()
            .expectStatus().isEqualTo(202)
    }
}
