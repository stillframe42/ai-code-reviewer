package stillframe42.aicodereviewer.rag.domain.port.`in`

import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.RagDocument

// 컨벤션 하이브리드 검색 인바운드 포트 — evaluation 이 소비한다
// topK/threshold 기본값은 RagProperties 에 의존하므로 null 로 받아 구현이 해석한다
interface ConventionSearchUseCase {
    suspend fun search(
        query: String,
        topK: Int? = null,
        category: ConventionCategory? = null,
        threshold: Double? = null,
    ): List<RagDocument>
}
