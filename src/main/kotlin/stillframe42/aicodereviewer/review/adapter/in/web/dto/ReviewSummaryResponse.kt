package stillframe42.aicodereviewer.review.adapter.`in`.web.dto

import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import java.time.Instant

// GET /api/reviews/{owner}/{repo}/{prNumber} 응답 DTO
data class ReviewSummaryResponse(
    val repoFullName: String,
    val prNumber: Int,
    val headSha: String,
    val status: ReviewRequestStatus,
    val createdAt: Instant,
    val completedAt: Instant?,
    val summary: String?,
    val issueCount: Int,
    val toolCallCount: Int,
    val modelName: String?,
)
