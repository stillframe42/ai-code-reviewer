package stillframe42.aicodereviewer.rag.domain.port.out

import org.springframework.ai.document.Document
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory

// 키워드 기반(Full-text Search) 컨벤션 검색 아웃바운드 포트
interface ConventionKeywordSearchPort {
    suspend fun search(
        query: String,
        topK: Int,
        category: ConventionCategory? = null,
    ): List<Document>
}
