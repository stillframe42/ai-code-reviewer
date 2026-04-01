package stillframe42.aicodereviewer.chat.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import java.time.Duration

// ChatController 통합 테스트 — RANDOM_PORT 실제 서버에 RestTestClient로 검증합니다.
class ChatControllerTest : AbstractIntegrationTest() {

    private lateinit var webClient: WebTestClient

    @BeforeEach
    fun setUp() {
        // SSE Flux 소비를 위해 WebTestClient 사용 (RestTestClient는 SSE 소비 API 미지원)
        webClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(60))
            .build()
        // WireMock은 LIFO 순서로 매칭 — 나중에 등록된 스텁이 먼저 검사된다
        // 스트리밍 스텁을 나중에 등록해야 stream=true 요청에 우선 매칭된다
        WireMockStubs.stubAnthropicChat(wireMock)
        WireMockStubs.stubAnthropicChatStream(wireMock)
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
        client.post().uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"message": "한 문장으로 답해주세요: 2+2는?"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.answer").exists()
    }

    @Test
    fun `빈 메시지로 스트리밍 요청 시 400 Bad Request 반환`() {
        client.post().uri("/api/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"message": ""}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `스트리밍 요청 시 SSE 형식으로 토큰을 수신한다`() {
        val tokens = webClient.post().uri("/api/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue("""{"message": "한 문장으로: 1+1은?", "provider": "ANTHROPIC"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String::class.java)
            .responseBody
            .take(1)
            .collectList()
            .block(Duration.ofSeconds(60))

        assertThat(tokens).isNotEmpty
    }
}
