package stillframe42.aicodereviewer.review.domain.port.out

import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewStatsResult
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewSummaryResult
import java.math.BigDecimal

// LLM 비용 집계 결과 — port/out 내부 집계용 타입 (전체 누적 비용 합계 + 호출 수)
data class LlmCostSummary(
    val totalCost: BigDecimal,
    val totalCalls: Long,
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
