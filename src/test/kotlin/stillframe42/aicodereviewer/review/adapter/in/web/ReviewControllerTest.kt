package stillframe42.aicodereviewer.review.adapter.`in`.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs

// ReviewController 통합 테스트 — RANDOM_PORT 실제 서버에 RestTestClient로 검증합니다.
class ReviewControllerTest : AbstractIntegrationTest() {

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubAnthropicReview(wireMock)
    }

    @Test
    fun `빈 code 필드 요청 시 400 Bad Request 반환`() {
        client.post().uri("/api/review")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"code": ""}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").exists()
    }

    @Test
    fun `code 필드 누락 시 400 Bad Request 반환`() {
        client.post().uri("/api/review")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `정상 코드 요청 시 200 OK와 구조화된 리뷰 결과 반환`() {
        client.post().uri("/api/review")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"code": "fun add(a: Int, b: Int) = a + b", "provider": "ANTHROPIC"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.overall_score").exists()
            .jsonPath("$.summary").exists()
            .jsonPath("$.issues").exists()
            .jsonPath("$.positives").exists()
    }

    @Test
    fun `provider 생략 시 기본값 ANTHROPIC으로 200 OK 반환`() {
        client.post().uri("/api/review")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"code": "fun multiply(a: Int, b: Int) = a * b"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.overall_score").exists()
            .jsonPath("$.summary").exists()
    }

    @Test
    fun `reviewMode=WITH_TOOLS이지만 installationId 누락 시 400 반환`() {
        // 자격증명 불필요 — 요청 유효성 검사이므로 항상 실행
        client.post().uri("/api/review")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"code": "fun foo() = 42", "reviewMode": "WITH_TOOLS"}""")
            .exchange()
            .expectStatus().isBadRequest
    }
}
