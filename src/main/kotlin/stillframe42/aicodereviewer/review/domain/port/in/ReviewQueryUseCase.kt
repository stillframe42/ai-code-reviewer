package stillframe42.aicodereviewer.review.domain.port.`in`

import stillframe42.aicodereviewer.review.domain.port.out.ReviewStatsResult
import stillframe42.aicodereviewer.review.domain.port.out.ReviewSummaryResult

// 리뷰 조회 인바운드 포트
interface ReviewQueryUseCase {
    suspend fun getReviewByPr(repoFullName: String, prNumber: Int): ReviewSummaryResult?
    suspend fun getStats(): ReviewStatsResult
}
