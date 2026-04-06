package stillframe42.aicodereviewer.review.application

import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewQueryUseCase
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStatsStore
import stillframe42.aicodereviewer.review.domain.port.out.ReviewQueryPort
import stillframe42.aicodereviewer.review.domain.port.out.ReviewStatsResult
import stillframe42.aicodereviewer.review.domain.port.out.ReviewSummaryResult

// 리뷰 조회 유스케이스 구현 — ReviewQueryPort와 ReviewCacheStatsStore를 조합하여 통계를 반환한다
@Service
class DefaultReviewQueryService(
    private val reviewQueryPort: ReviewQueryPort,
    private val reviewCacheStatsStore: ReviewCacheStatsStore,
) : ReviewQueryUseCase {

    override suspend fun getReviewByPr(
        repoFullName: String,
        prNumber: Int,
    ): ReviewSummaryResult? = reviewQueryPort.findLatestByRepoAndPr(repoFullName, prNumber)

    override suspend fun getStats(): ReviewStatsResult {
        val hitCount = reviewCacheStatsStore.getHitCount()
        val missCount = reviewCacheStatsStore.getMissCount()
        val totalCacheOps = hitCount + missCount
        val cacheHitRate = if (totalCacheOps == 0L) 0.0 else hitCount.toDouble() / totalCacheOps

        val costSummary = reviewQueryPort.totalLlmCostSummary()
        val averageCostPerCall = if (costSummary.totalCalls == 0L) {
            BigDecimal.ZERO
        } else {
            costSummary.totalCost.divide(BigDecimal(costSummary.totalCalls), 6, RoundingMode.HALF_UP)
        }
        val estimatedSavings = BigDecimal(hitCount).multiply(averageCostPerCall)

        return ReviewStatsResult(
            totalReviews = reviewQueryPort.countTotalReviews(),
            categoryDistribution = reviewQueryPort.countByCategory(),
            averageToolCallCount = reviewQueryPort.averageToolCallCount(),
            costByModel = reviewQueryPort.sumCostByModel(),
            cacheHitRate = cacheHitRate,
            estimatedSavings = estimatedSavings,
        )
    }
}
