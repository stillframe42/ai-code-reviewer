package stillframe42.aicodereviewer.review.adapter.out.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewSummaryResult
import stillframe42.aicodereviewer.review.domain.port.out.LlmCostSummary
import stillframe42.aicodereviewer.review.domain.port.out.ReviewQueryPort
import java.math.BigDecimal

// ReviewQueryPort 구현체 — JPA Repository로 리뷰 통계/조회를 처리한다
@Component
class ReviewQueryAdapter(
    private val reviewRequestRepository: ReviewRequestRepository,
    private val reviewResultRepository: ReviewResultRepository,
    private val reviewIssueCategoryRepository: ReviewIssueCategoryRepository,
    private val llmCostLogRepository: LlmCostLogRepository,
) : ReviewQueryPort {

    override suspend fun findLatestByRepoAndPr(
        repoFullName: String,
        prNumber: Int,
    ): ReviewSummaryResult? = withContext(Dispatchers.IO) {
        val request = reviewRequestRepository
            .findTopByRepoFullNameAndPrNumberOrderByCreatedAtDesc(repoFullName, prNumber)
            ?: return@withContext null
        val result = reviewResultRepository.findByReviewRequestId(request.id)
        val issueCount = result?.let {
            reviewIssueCategoryRepository.countByReviewResultId(it.id).toInt()
        } ?: 0
        ReviewSummaryResult(
            repoFullName = request.repoFullName,
            prNumber = request.prNumber,
            headSha = request.headSha,
            status = request.status,
            createdAt = request.createdAt,
            completedAt = request.completedAt,
            summary = result?.summary,
            issueCount = issueCount,
            toolCallCount = result?.toolCallCount ?: 0,
            modelName = result?.modelName,
        )
    }

    override suspend fun countTotalReviews(): Long = withContext(Dispatchers.IO) {
        reviewRequestRepository.count()
    }

    override suspend fun countByCategory(): Map<IssueCategory, Long> = withContext(Dispatchers.IO) {
        chunkedSequence { reviewIssueCategoryRepository.findAllBy(it) }
            .groupingBy { it.category }
            .fold(0L) { acc, _ -> acc + 1L }
            .let { counts -> IssueCategory.entries.associateWith { counts[it] ?: 0L } }
    }

    override suspend fun averageToolCallCount(): Double = withContext(Dispatchers.IO) {
        chunkedSequence { reviewResultRepository.findAllBy(it) }
            .map { it.toolCallCount.toLong() }
            .fold(0L to 0L) { (sum, count), v -> (sum + v) to (count + 1L) }
            .let { (sum, count) -> if (count == 0L) 0.0 else sum.toDouble() / count }
    }

    override suspend fun sumCostByModel(): Map<String, BigDecimal> = withContext(Dispatchers.IO) {
        chunkedSequence { llmCostLogRepository.findAllBy(it) }
            .groupingBy { it.modelName }
            .fold(BigDecimal.ZERO) { acc, entity -> acc + entity.estimatedCostUsd }
    }

    override suspend fun totalLlmCostSummary(): LlmCostSummary = withContext(Dispatchers.IO) {
        chunkedSequence { llmCostLogRepository.findAllBy(it) }
            .fold(BigDecimal.ZERO to 0L) { (sum, count), entity -> (sum + entity.estimatedCostUsd) to (count + 1L) }
            .let { (totalCost, totalCalls) -> LlmCostSummary(totalCost = totalCost, totalCalls = totalCalls) }
    }

    // 청크(1000건) 단위로 전체 레코드를 순회하는 Sequence — 집계 쿼리 대신 I/O 분산
    private fun <T : Any> chunkedSequence(fetch: (Pageable) -> Page<T>): Sequence<T> =
        generateSequence(fetch(PageRequest.of(0, 1000))) { prev ->
            if (prev.hasNext()) fetch(prev.nextPageable()) else null
        }.flatten()
}
