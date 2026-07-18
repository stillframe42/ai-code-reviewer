package stillframe42.aicodereviewer.rag.adapter.out.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort

// PostgreSQL Full-text Search(tsvector/tsquery) 기반 키워드 검색 어댑터
@Component
class JdbcConventionKeywordAdapter(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : ConventionKeywordSearchPort {

    override fun search(
        query: String,
        topK: Int,
        category: ConventionCategory?,
    ): List<RagDocument> {
        // 빈 쿼리는 plainto_tsquery 파싱 오류 방지를 위해 조기 반환
        if (query.isBlank()) return emptyList()
        // category가 non-null이면 metadata JSONB 필드로 카테고리 필터 적용
        val categoryClause = if (category != null) "AND metadata->>'category' = ?" else ""
        val sql = """
            SELECT id::text, content, metadata::text
            FROM vector_store
            WHERE content_tsv @@ plainto_tsquery('simple', ?)
            $categoryClause
            ORDER BY ts_rank(content_tsv, plainto_tsquery('simple', ?)) DESC
            LIMIT ?
        """.trimIndent()
        val args = buildList<Any> {
            add(query)
            if (category != null) add(category.name)
            add(query)
            add(topK)
        }.toTypedArray()
        return jdbcTemplate.query(sql, { rs, _ ->
            RagDocument(
                id = rs.getString("id"),
                text = rs.getString("content"),
                metadata = objectMapper.readValue<Map<String, Any>>(rs.getString("metadata")),
            )
        }, *args)
    }
}
