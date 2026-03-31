package stillframe42.aicodereviewer.review.adapter.`in`.web.dto

import stillframe42.aicodereviewer.review.domain.model.IssueCategory

// GET /api/reviews/stats 응답 DTO
data class ReviewStatsResponse(
    val totalReviews: Long,
    val categoryDistribution: Map<IssueCategory, Long>,
    val averageToolCallCount: Double,
)
