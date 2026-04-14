package stillframe42.aicodereviewer.rag.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.document.Document
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.config.RagProperties
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.out.ContextCompressorPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import stillframe42.aicodereviewer.rag.domain.service.reciprocalRankFusion

@Service
class HybridConventionSearchService(
    private val vectorPort: ConventionVectorPort,
    private val keywordPort: ConventionKeywordSearchPort,
    private val ragProperties: RagProperties,
    private val contextCompressor: ContextCompressorPort,
) {
    // 일반 검색 — 벡터 + 키워드 + RRF + 압축 (전체 파이프라인)
    // 프로덕션 코드는 이 메서드를 사용한다.
    suspend fun search(
        query: String,
        topK: Int = 5,
        category: ConventionCategory? = null,
    ): List<Document> {
        val rawResults = searchRaw(query, topK, category)
        return contextCompressor.compress(query, rawResults)
    }

    // 압축 우회 검색 — 벡터 + 키워드 + RRF만 (압축 없음)
    // Phase 4 압축 전/후 비교 측정용 — 프로덕션 코드는 search()를 사용한다.
    suspend fun searchRaw(
        query: String,
        topK: Int = 5,
        category: ConventionCategory? = null,
    ): List<Document> {
        val candidateSize = topK * 2

        // ConventionVectorPort.search()는 suspend가 아닌 블로킹 함수 — IO 스레드풀에서 실행
        val vectorResults = withContext(Dispatchers.IO) {
            vectorPort.search(query, candidateSize, category, ragProperties.similarityThreshold)
        }
        // 벡터·키워드 동일한 category 범위로 검색하여 RRF 결과의 카테고리 일관성 보장
        val keywordResults = keywordPort.search(query, candidateSize, category)

        return reciprocalRankFusion(vectorResults, keywordResults, topK)
    }
}
