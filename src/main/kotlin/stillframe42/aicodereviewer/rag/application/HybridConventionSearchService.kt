package stillframe42.aicodereviewer.rag.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.config.RagProperties
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionSearchUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ContextCompressorPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import stillframe42.aicodereviewer.rag.domain.port.out.MultiQueryGeneratorPort
import stillframe42.aicodereviewer.rag.domain.service.reciprocalRankFusion

@Service
class HybridConventionSearchService(
    private val vectorPort: ConventionVectorPort,
    private val keywordPort: ConventionKeywordSearchPort,
    private val ragProperties: RagProperties,
    private val contextCompressor: ContextCompressorPort,
    private val observabilityPort: ObservabilityPort,
    private val multiQueryGeneratorPort: MultiQueryGeneratorPort,
) : ConventionSearchUseCase {
    // 일반 검색 — 벡터 + 키워드 + RRF + 압축 (전체 파이프라인). 프로덕션 진입점.
    // ARCH 카테고리만 압축을 우회한다: 파일명 쿼리와 ARCH 룰의 표면적 거리가 커서
    // context-compressor 가 NONE 판정으로 통째 drop 하는 regression 이 관찰됐음.
    override fun search(
        query: String,
        topK: Int?,
        category: ConventionCategory?,
        threshold: Double?,
    ): List<RagDocument> {
        val effectiveTopK = topK ?: ragProperties.topK
        val effectiveThreshold = threshold ?: ragProperties.similarityThreshold
        // multiQueryEnabled=true 시 원본 + LLM 변형 3개로 fan-out 검색 후 N-list RRF 통합
        val rawResults = if (ragProperties.multiQueryEnabled) {
            val queries = multiQueryGeneratorPort.generateMultipleQueries(query)
            val perQueryResults = queries.map { q -> searchRaw(q, effectiveTopK, category, effectiveThreshold) }
            reciprocalRankFusion(perQueryResults, effectiveTopK)
        } else {
            searchRaw(query, effectiveTopK, category, effectiveThreshold)
        }
        if (category == ConventionCategory.ARCH) return rawResults
        return contextCompressor.compress(query, rawResults)
    }

    // 압축 우회 검색 — 벡터 + 키워드 + RRF만 (압축 전/후 비교 측정용, 프로덕션은 search() 사용)
    fun searchRaw(
        query: String,
        topK: Int = ragProperties.topK,
        category: ConventionCategory? = null,
        threshold: Double = ragProperties.similarityThreshold,
    ): List<RagDocument> {
        // styleFilterBypass=true 시 STYLE 카테고리만 필터를 우회 (범용 룰이라 풀이 과도하게 좁아지는 문제 회피)
        val effectiveCategory = if (ragProperties.styleFilterBypass && category == ConventionCategory.STYLE) null
                               else category

        val handle = observabilityPort.startSpan(
            name = "rag.hybrid-search",
            input = mapOf(
                "query" to query,
                "topK" to topK,
                "category" to category.nameOrAll(),
                "effectiveCategory" to effectiveCategory.nameOrAll(),
                "threshold" to threshold,
            ),
        )
        return try {
            val candidateSize = topK * 2

            val vectorResults = vectorPort.search(query, candidateSize, effectiveCategory, threshold)
            // 벡터·키워드 동일한 effectiveCategory 범위로 검색하여 RRF 결과의 카테고리 일관성 보장
            val keywordResults = keywordPort.search(query, candidateSize, effectiveCategory)

            val rrf = reciprocalRankFusion(vectorResults, keywordResults, topK)
            observabilityPort.endSpan(
                handle,
                output = mapOf(
                    "vector_hit_count" to vectorResults.size,
                    "keyword_hit_count" to keywordResults.size,
                    "rrf_top_doc_ids" to rrf.take(5).map { it.id ?: "unknown" },
                ),
            )
            rrf
        } catch (e: Throwable) {
            observabilityPort.endSpanWithError(handle, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }
}
