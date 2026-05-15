package stillframe42.aicodereviewer.rag.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

// Spring AI VectorStore를 ConventionVectorPort로 감싸는 아웃바운드 어댑터
// VectorStore API가 지원하지 않는 경우 JdbcTemplate을 직접 사용한다.
@Component
class ConventionVectorAdapter(
    private val vectorStore: VectorStore,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : ConventionVectorPort {

    override fun save(documents: List<Document>) {
        vectorStore.add(documents)
    }

    override fun isEmpty(): Boolean =
        (jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store",
            Long::class.java,
        ) ?: 0L) == 0L

    override fun deleteAll() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store")
    }

    // query를 임베딩(OpenAI API 호출)하여 pgvector cosine 유사도 검색 수행
    // category가 지정되면 metadata JSONB 필터를 적용하여 해당 카테고리 문서만 검색한다
    override fun search(query: String, topK: Int, category: ConventionCategory?, similarityThreshold: Double): List<Document> =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .apply { category?.let { filterExpression("category == '${it.name}'") } }
                .build()
        )

    override fun findById(id: String): Document? =
        runCatching {
            jdbcTemplate.queryForObject(
                "SELECT id::text, content, metadata::text FROM vector_store WHERE id = ?::uuid",
                { rs, _ ->
                    Document(
                        rs.getString("id"),
                        rs.getString("content"),
                        objectMapper.readValue<Map<String, Any>>(rs.getString("metadata")),
                    )
                },
                id,
            )
        }.getOrNull()
}
