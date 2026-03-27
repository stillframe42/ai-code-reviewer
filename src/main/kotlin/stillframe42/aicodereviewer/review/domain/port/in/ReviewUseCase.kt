package stillframe42.aicodereviewer.review.domain.port.`in`

import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.ReviewMode

// 코드 리뷰 기능 입력 포트 — 도메인이 외부에 제공하는 유스케이스 인터페이스
interface ReviewUseCase {
    suspend fun reviewCode(
        code: String,
        provider: AiProvider,
        diffOptions: DiffFilterOptions? = null,
        mode: ReviewMode = ReviewMode.Simple,
    ): CodeReview
}
