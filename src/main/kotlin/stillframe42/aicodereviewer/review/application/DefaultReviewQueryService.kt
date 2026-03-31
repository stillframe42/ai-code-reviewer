package stillframe42.aicodereviewer.review.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewQueryUseCase
import stillframe42.aicodereviewer.review.domain.port.out.ReviewQueryPort
import stillframe42.aicodereviewer.review.domain.port.out.ReviewStatsResult
import stillframe42.aicodereviewer.review.domain.port.out.ReviewSummaryResult

// 리뷰 조회 유스케이스 구현 — ReviewQueryPort에서 읽어 domain 결과 타입으로 반환한다
@Service
class DefaultReviewQueryService(
    private val reviewQueryPort: ReviewQueryPort,
) : ReviewQueryUseCase {

    override suspend fun getReviewByPr(
        repoFullName: String,
        prNumber: Int,
    ): ReviewSummaryResult? = reviewQueryPort.findLatestByRepoAndPr(repoFullName, prNumber)

    override suspend fun getStats(): ReviewStatsResult = ReviewStatsResult(
        totalReviews = reviewQueryPort.countTotalReviews(),
        categoryDistribution = reviewQueryPort.countByCategory(),
        averageToolCallCount = reviewQueryPort.averageToolCallCount(),
    )
}
