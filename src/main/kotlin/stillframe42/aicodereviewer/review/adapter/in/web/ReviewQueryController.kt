package stillframe42.aicodereviewer.review.adapter.`in`.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewStatsResponse
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewSummaryResponse
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewQueryUseCase
import stillframe42.aicodereviewer.review.domain.port.out.ReviewStatsResult
import stillframe42.aicodereviewer.review.domain.port.out.ReviewSummaryResult

@RestController
@RequestMapping("/api/reviews")
class ReviewQueryController(private val reviewQueryUseCase: ReviewQueryUseCase) {

    // 특정 PR의 최신 리뷰 조회 — 없으면 404
    @GetMapping("/{owner}/{repo}/{prNumber}")
    suspend fun getReviewByPr(
        @PathVariable owner: String,
        @PathVariable repo: String,
        @PathVariable prNumber: Int,
    ): ResponseEntity<ReviewSummaryResponse> {
        val repoFullName = "$owner/$repo"
        return reviewQueryUseCase.getReviewByPr(repoFullName, prNumber)
            ?.let { ResponseEntity.ok(it.toResponse()) }
            ?: ResponseEntity.notFound().build()
    }

    // 전체 리뷰 통계 조회
    @GetMapping("/stats")
    suspend fun getStats(): ReviewStatsResponse = reviewQueryUseCase.getStats().toResponse()
}

private fun ReviewSummaryResult.toResponse() = ReviewSummaryResponse(
    repoFullName = repoFullName,
    prNumber = prNumber,
    headSha = headSha,
    status = status,
    createdAt = createdAt,
    completedAt = completedAt,
    summary = summary,
    issueCount = issueCount,
    toolCallCount = toolCallCount,
    modelName = modelName,
)

private fun ReviewStatsResult.toResponse() = ReviewStatsResponse(
    totalReviews = totalReviews,
    categoryDistribution = categoryDistribution,
    averageToolCallCount = averageToolCallCount,
    costByModel = costByModel,
    cacheHitRate = cacheHitRate,
    estimatedSavings = estimatedSavings,
)
