package stillframe42.aicodereviewer.rag.adapter.`in`.web

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

@TestPropertySource(properties = ["agent.python.callback.internal-auth-token=test-rag-token"])
class RagContextControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `200 — 유효 토큰 + 존재 ID 는 content 반환`() {
        val id = seedRow(content = "the chunk body")

        val response = client.get()
            .uri("/api/rag/context/{id}", id)
            .header("X-Internal-Auth", "test-rag-token")
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult().responseBody!!

        assertThat(response["content"]).isEqualTo("the chunk body")
    }

    @Test
    fun `404 — 유효 토큰 + 존재하지 않는 UUID`() {
        val missing = UUID.randomUUID().toString()
        client.get()
            .uri("/api/rag/context/{id}", missing)
            .header("X-Internal-Auth", "test-rag-token")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `404 — 유효 토큰 + 비 UUID 형식`() {
        client.get()
            .uri("/api/rag/context/{id}", "not-a-uuid")
            .header("X-Internal-Auth", "test-rag-token")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `401 — X-Internal-Auth 헤더 누락`() {
        val id = seedRow(content = "x")
        client.get()
            .uri("/api/rag/context/{id}", id)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `401 — 잘못된 토큰`() {
        val id = seedRow(content = "x")
        client.get()
            .uri("/api/rag/context/{id}", id)
            .header("X-Internal-Auth", "wrong-token")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun seedRow(content: String): String {
        val id = UUID.randomUUID()
        val metadata = objectMapper.writeValueAsString(mapOf("category" to "SECURITY"))
        val zeroVector = "[" + (1..1536).joinToString(",") { "0.1" } + "]"
        jdbcTemplate.update(
            "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::jsonb, ?::vector)",
            id.toString(), content, metadata, zeroVector,
        )
        return id.toString()
    }
}
