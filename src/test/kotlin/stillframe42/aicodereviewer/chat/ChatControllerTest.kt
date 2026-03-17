package stillframe42.aicodereviewer.chat

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

// ChatController 통합 테스트 — RANDOM_PORT 실제 서버에 RestTestClient로 검증합니다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatControllerTest {

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @BeforeEach
    fun setUp() {
        client = RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }

    @Test
    fun `빈 메시지 요청 시 400 Bad Request 반환`() {
        client.post().uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"message": ""}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.message").exists()
    }

    @Test
    fun `message 필드 누락 시 400 Bad Request 반환`() {
        client.post().uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `정상 메시지 요청 시 200 OK와 AI 응답 반환`() {
        // 더미 키인 경우 테스트 스킵
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        client.post().uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"message": "한 문장으로 답해주세요: 1+1은?", "provider": "ANTHROPIC"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.answer").exists()
    }

    @Test
    fun `provider 생략 시 기본값 ANTHROPIC으로 200 OK 반환`() {
        // 더미 키인 경우 테스트 스킵
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        client.post().uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"message": "한 문장으로 답해주세요: 2+2는?"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.answer").exists()
    }
}
