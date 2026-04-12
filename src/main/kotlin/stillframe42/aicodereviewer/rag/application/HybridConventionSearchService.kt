package stillframe42.aicodereviewer.rag.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.document.Document
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import stillframe42.aicodereviewer.rag.domain.service.reciprocalRankFusion

@Service
class HybridConventionSearchService(
    private val vectorPort: ConventionVectorPort,
    private val keywordPort: ConventionKeywordSearchPort,
) {
    suspend fun search(
        query: String,
        topK: Int = 5,
        category: ConventionCategory? = null,
    ): List<Document> {
        val candidateSize = topK * 2

        // ConventionVectorPort.search()는 suspend가 아닌 블로킹 함수 — IO 스레드풀에서 실행
        val vectorResults = withContext(Dispatchers.IO) {
            vectorPort.search(query, candidateSize, category)
        }
        val keywordResults = keywordPort.search(query, candidateSize)

        return reciprocalRankFusion(vectorResults, keywordResults, topK)
    }
}
