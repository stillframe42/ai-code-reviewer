package stillframe42.aicodereviewer.rag.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

class ConventionVectorAdapterFindByIdIT : AbstractIntegrationTest() {

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `findById — 존재하는 ID 는 Document 를 반환한다`() {
        val id = UUID.randomUUID()
        seedRow(id, content = "convention chunk body", categoryName = "SECURITY")

        val doc = vectorPort.findById(id.toString())

        assertThat(doc).isNotNull
        assertThat(doc!!.id).isEqualTo(id.toString())
        assertThat(doc.text).isEqualTo("convention chunk body")
        assertThat(doc.metadata["category"]).isEqualTo("SECURITY")
    }

    @Test
    fun `findById — 존재하지 않는 UUID 는 null 을 반환한다`() {
        val missingId = UUID.randomUUID().toString()
        val doc = vectorPort.findById(missingId)
        assertThat(doc).isNull()
    }

    @Test
    fun `findById — UUID 형식이 아니면 null 을 반환한다`() {
        val doc = vectorPort.findById("not-a-uuid")
        assertThat(doc).isNull()
    }

    private fun seedRow(id: UUID, content: String, categoryName: String) {
        val metadata = objectMapper.writeValueAsString(mapOf("category" to categoryName))
        val zeroVector = "[" + (1..1536).joinToString(",") { "0.1" } + "]"
        jdbcTemplate.update(
            "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::jsonb, ?::vector)",
            id.toString(), content, metadata, zeroVector,
        )
    }
}
