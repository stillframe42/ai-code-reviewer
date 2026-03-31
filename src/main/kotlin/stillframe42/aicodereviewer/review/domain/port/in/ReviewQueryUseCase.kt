package stillframe42.aicodereviewer.review.domain.port.`in`

import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewStatsResponse
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewSummaryResponse

// 리뷰 조회 인바운드 포트
interface ReviewQueryUseCase {
    suspend fun getReviewByPr(repoFullName: String, prNumber: Int): ReviewSummaryResponse?
    suspend fun getStats(): ReviewStatsResponse
}
