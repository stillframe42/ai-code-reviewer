package stillframe42.aicodereviewer.review.adapter.out.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.port.out.ReviewQueryPort
import stillframe42.aicodereviewer.review.domain.port.out.ReviewSummaryResult

// ReviewQueryPort 구현체 — JPA Repository로 리뷰 통계/조회를 처리한다
@Component
class ReviewQueryAdapter(
    private val reviewRequestRepository: ReviewRequestRepository,
    private val reviewResultRepository: ReviewResultRepository,
    private val reviewIssueCategoryRepository: ReviewIssueCategoryRepository,
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
        val counts = reviewIssueCategoryRepository.countByCategory()
        // IssueCategory 4개 모두 포함 — 없는 카테고리는 0
        IssueCategory.entries.associateWith { cat ->
            counts.find { it.getCategory() == cat }?.getCount() ?: 0L
        }
    }

    override suspend fun averageToolCallCount(): Double = withContext(Dispatchers.IO) {
        reviewResultRepository.averageToolCallCount()
    }
}
