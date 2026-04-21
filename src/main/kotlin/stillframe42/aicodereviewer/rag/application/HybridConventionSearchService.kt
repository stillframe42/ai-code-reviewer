package stillframe42.aicodereviewer.rag.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.document.Document
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.config.RagProperties
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.out.ContextCompressorPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import stillframe42.aicodereviewer.rag.domain.service.MultiQueryGenerator
import stillframe42.aicodereviewer.rag.domain.service.reciprocalRankFusion

@Service
class HybridConventionSearchService(
    private val vectorPort: ConventionVectorPort,
    private val keywordPort: ConventionKeywordSearchPort,
    private val ragProperties: RagProperties,
    private val contextCompressor: ContextCompressorPort,
    private val observabilityPort: ObservabilityPort,
    private val multiQueryGenerator: MultiQueryGenerator,
) {
    // 일반 검색 — 벡터 + 키워드 + RRF + 압축 (전체 파이프라인)
    // 프로덕션 코드는 이 메서드를 사용한다.
    //
    // Direction B: ARCH 카테고리는 압축을 우회한다.
    // ARCH 규칙(헥사고날 레이어, 포트-어댑터, 트랜잭션 경계 등)은 쿼리 파일명과
    // 표면적으로 관련 없어 보이는 경우가 많아(예: "OrderService.kt" 쿼리에 대한
    // "Controller는 UseCase에만 의존" chunk) context-compressor의 NONE 판정으로
    // 통째 drop되는 regression이 `compression-comparison_2.md` arch-1 케이스에서
    // 확인됐다. ARCH 카테고리만 우회해 최소 변경으로 retrieval 완전성을 보존한다.
    // 다른 카테고리(API/SECURITY/STYLE)는 압축 유지 — 토큰 절감 효과 유지.
    suspend fun search(
        query: String,
        topK: Int = 5,
        category: ConventionCategory? = null,
        threshold: Double = ragProperties.similarityThreshold,
    ): List<Document> {
        // multiQueryEnabled=true 시 원본 + LLM 변형 3개로 fan-out 검색 후 N-list RRF 통합 (B-3)
        val rawResults = if (ragProperties.multiQueryEnabled) {
            val queries = multiQueryGenerator.generateMultipleQueries(query)
            val perQueryResults = queries.map { q -> searchRaw(q, topK, category, threshold) }
            reciprocalRankFusion(perQueryResults, topK)
        } else {
            searchRaw(query, topK, category, threshold)
        }
        if (category == ConventionCategory.ARCH) return rawResults
        return contextCompressor.compress(query, rawResults)
    }

    // 압축 우회 검색 — 벡터 + 키워드 + RRF만 (압축 없음)
    // Phase 4 압축 전/후 비교 측정용 — 프로덕션 코드는 search()를 사용한다.
    suspend fun searchRaw(
        query: String,
        topK: Int = 5,
        category: ConventionCategory? = null,
        threshold: Double = ragProperties.similarityThreshold,
    ): List<Document> {
        // STYLE 카테고리는 범용 룰이라 필터링이 검색 풀을 과도하게 좁힘 (Step A-4).
        // styleFilterBypass=true 시 STYLE만 카테고리 필터를 우회한다.
        val effectiveCategory = if (ragProperties.styleFilterBypass && category == ConventionCategory.STYLE) null
                               else category

        val handle = observabilityPort.startSpan(
            name = "rag.hybrid-search",
            input = mapOf(
                "query" to query,
                "topK" to topK,
                "category" to (category?.name ?: "ALL"),
                "effectiveCategory" to (effectiveCategory?.name ?: "ALL"),
                "threshold" to threshold,
            ),
        )
        return try {
            val candidateSize = topK * 2

            // ConventionVectorPort.search()는 suspend가 아닌 블로킹 함수 — IO 스레드풀에서 실행
            val vectorResults = withContext(Dispatchers.IO) {
                vectorPort.search(query, candidateSize, effectiveCategory, threshold)
            }
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
