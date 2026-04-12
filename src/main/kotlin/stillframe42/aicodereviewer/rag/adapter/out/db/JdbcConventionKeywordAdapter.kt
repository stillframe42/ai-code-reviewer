package stillframe42.aicodereviewer.rag.adapter.out.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.document.Document
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort

// PostgreSQL Full-text Search(tsvector/tsquery) 기반 키워드 검색 어댑터
@Component
class JdbcConventionKeywordAdapter(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : ConventionKeywordSearchPort {

    override suspend fun search(query: String, topK: Int): List<Document> =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                """
                SELECT id::text, content, metadata::text
                FROM vector_store
                WHERE content_tsv @@ plainto_tsquery('english', ?)
                ORDER BY ts_rank(content_tsv, plainto_tsquery('english', ?)) DESC
                LIMIT ?
                """.trimIndent(),
                { rs, _ ->
                    Document(
                        rs.getString("id"),
                        rs.getString("content"),
                        objectMapper.readValue<Map<String, Any>>(rs.getString("metadata")),
                    )
                },
                query, query, topK,
            )
        }
}
