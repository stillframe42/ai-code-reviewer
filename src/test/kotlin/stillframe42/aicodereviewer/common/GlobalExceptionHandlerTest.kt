package stillframe42.aicodereviewer.common

import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// GlobalExceptionHandler 통합 테스트 — 존재하지 않는 경로에 대한 응답 코드를 검증합니다.
class GlobalExceptionHandlerTest : AbstractIntegrationTest() {

    @Test
    fun `존재하지 않는 경로로 요청하면 500이 아닌 404 Not Found 반환`() {
        client.post().uri("/")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `존재하지 않는 GET 경로도 404 Not Found 반환`() {
        client.get().uri("/not-found")
            .exchange()
            .expectStatus().isNotFound
    }
}
