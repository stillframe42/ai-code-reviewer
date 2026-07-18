package stillframe42.aicodereviewer.review.domain.port.`in`

import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewStatsResult
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewSummaryResult

// 리뷰 조회 인바운드 포트
interface ReviewQueryUseCase {
    fun getReviewByPr(repoFullName: String, prNumber: Int): ReviewSummaryResult?
    fun getStats(): ReviewStatsResult
}
