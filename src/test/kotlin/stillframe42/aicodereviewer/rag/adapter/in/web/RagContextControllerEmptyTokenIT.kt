package stillframe42.aicodereviewer.rag.adapter.`in`.web

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

@TestPropertySource(properties = ["agent.python.callback.internal-auth-token="])
class RagContextControllerEmptyTokenIT : AbstractIntegrationTest() {

    @Test
    fun `401 — 빈 토큰 설정 시 어떤 헤더든 거절`() {
        client.get()
            .uri("/api/rag/context/{id}", UUID.randomUUID())
            .header("X-Internal-Auth", "any-token")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
