package stillframe42.aicodereviewer.review.domain.port.out

import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import java.math.BigDecimal
import java.time.Instant

// 어댑터 → 포트 반환용 내부 데이터 클래스 (ReviewRequestEntity + ReviewResultEntity 조합)
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

// LLM 비용 집계 결과 — 전체 누적 비용 합계 + 호출 수
data class LlmCostSummary(
    val totalCost: BigDecimal,
    val totalCalls: Long,
)

// 리뷰 통계 조회 결과 — UseCase 반환용 도메인 데이터
data class ReviewStatsResult(
    val totalReviews: Long,
    val categoryDistribution: Map<IssueCategory, Long>,
    val averageToolCallCount: Double,
    val costByModel: Map<String, BigDecimal>,
    val cacheHitRate: Double,
    val estimatedSavings: BigDecimal,
)

// 리뷰 조회 아웃바운드 포트 — 읽기 전용
interface ReviewQueryPort {

    // 특정 레포/PR의 최신 리뷰 요청+결과 조합 조회 — 없으면 null
    suspend fun findLatestByRepoAndPr(repoFullName: String, prNumber: Int): ReviewSummaryResult?

    // 전체 리뷰 요청 수
    suspend fun countTotalReviews(): Long

    // 카테고리별 이슈 수 집계 — IssueCategory 4개 모두 포함 (없는 카테고리는 0)
    suspend fun countByCategory(): Map<IssueCategory, Long>

    // 전체 리뷰의 평균 Tool 호출 횟수
    suspend fun averageToolCallCount(): Double

    // 모델명별 누적 LLM 비용 합계 (llm_cost_logs GROUP BY model_name)
    suspend fun sumCostByModel(): Map<String, BigDecimal>

    // 전체 LLM 호출 누적 비용 합계 + 호출 수 (estimatedSavings 계산용)
    suspend fun totalLlmCostSummary(): LlmCostSummary
}
