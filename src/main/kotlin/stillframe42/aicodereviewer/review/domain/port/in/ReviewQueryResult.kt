package stillframe42.aicodereviewer.review.domain.port.`in`

import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import java.math.BigDecimal
import java.time.Instant

// 리뷰 조회 유스케이스 반환 타입 — PR 요청·결과를 조합한 요약 정보
data class ReviewSummaryResult(
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

// 전체 리뷰 통계 조회 결과 — UseCase 반환용 도메인 데이터
data class ReviewStatsResult(
    val totalReviews: Long,
    val categoryDistribution: Map<IssueCategory, Long>,
    val averageToolCallCount: Double,
    val costByModel: Map<String, BigDecimal>,
    val cacheHitRate: Double,
    val estimatedSavings: BigDecimal,
)
