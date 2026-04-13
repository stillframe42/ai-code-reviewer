package stillframe42.aicodereviewer.review.domain.port.out

import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.ReviewContext
import stillframe42.aicodereviewer.review.domain.model.ReviewMode

// 코드 리뷰 기능 출력 포트 — 도메인이 AI 인프라에 요청하는 인터페이스
interface AiReviewPort {
    suspend fun reviewCode(
        code: String,
        provider: AiProvider,
        mode: ReviewMode = ReviewMode.Simple,
        reviewContext: ReviewContext? = null,
        modelName: String? = null,
        conventionContext: String? = null,   // RAG로 조회한 컨벤션 문서 컨텍스트
    ): CodeReview
}
