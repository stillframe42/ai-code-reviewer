package stillframe42.aicodereviewer.review.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewStatsResponse
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewSummaryResponse
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewQueryUseCase
import stillframe42.aicodereviewer.review.domain.port.out.ReviewQueryPort
import stillframe42.aicodereviewer.review.domain.port.out.ReviewSummaryResult

// 리뷰 조회 유스케이스 구현 — ReviewQueryPort에서 읽어 DTO로 변환한다
@Service
class DefaultReviewQueryService(
    private val reviewQueryPort: ReviewQueryPort,
) : ReviewQueryUseCase {

    override suspend fun getReviewByPr(
        repoFullName: String,
        prNumber: Int,
    ): ReviewSummaryResponse? = reviewQueryPort
        .findLatestByRepoAndPr(repoFullName, prNumber)
        ?.let(::toResponse)

    override suspend fun getStats(): ReviewStatsResponse = ReviewStatsResponse(
        totalReviews = reviewQueryPort.countTotalReviews(),
        categoryDistribution = reviewQueryPort.countByCategory(),
        averageToolCallCount = reviewQueryPort.averageToolCallCount(),
    )

    private fun toResponse(result: ReviewSummaryResult) = ReviewSummaryResponse(
        repoFullName = result.repoFullName,
        prNumber = result.prNumber,
        headSha = result.headSha,
        status = result.status,
        createdAt = result.createdAt,
        completedAt = result.completedAt,
        summary = result.summary,
        issueCount = result.issueCount,
        toolCallCount = result.toolCallCount,
        modelName = result.modelName,
    )
}
