package stillframe42.aicodereviewer.review.adapter.out.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewIssueCategoryEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewRequestEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewResultEntity
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import stillframe42.aicodereviewer.review.domain.port.out.ReviewPersistencePort
import java.time.Instant

// ReviewPersistencePort 구현체 — JPA를 통해 리뷰 요청/결과/카테고리를 저장한다
// JPA는 블로킹 API이므로 모든 메서드를 Dispatchers.IO에서 실행한다
@Component
class ReviewPersistenceAdapter(
    private val reviewRequestRepository: ReviewRequestRepository,
    private val reviewResultRepository: ReviewResultRepository,
    private val reviewIssueCategoryRepository: ReviewIssueCategoryRepository,
    @param:Qualifier("jackson2ObjectMapper")
    private val objectMapper: ObjectMapper,
) : ReviewPersistencePort {

    override suspend fun saveReviewRequest(
        repoFullName: String,
        prNumber: Int,
        headSha: String,
    ): Long = withContext(Dispatchers.IO) {
        reviewRequestRepository.save(
            ReviewRequestEntity(
                repoFullName = repoFullName,
                prNumber = prNumber,
                headSha = headSha,
                status = ReviewRequestStatus.PENDING,
            ),
        ).id
    }

    override suspend fun updateReviewStatus(
        id: Long,
        status: ReviewRequestStatus,
        completedAt: Instant?,
    ): Unit = withContext(Dispatchers.IO) {
        val entity = reviewRequestRepository.findById(id).orElseThrow {
            IllegalArgumentException("ReviewRequest not found: $id")
        }
        reviewRequestRepository.save(
            ReviewRequestEntity(
                id = entity.id,
                repoFullName = entity.repoFullName,
                prNumber = entity.prNumber,
                headSha = entity.headSha,
                status = status,
                createdAt = entity.createdAt,
                completedAt = completedAt ?: entity.completedAt,
            ),
        )
    }

    // withContext(Dispatchers.IO)에서 @Transactional이 전파되지 않으므로
    // 블로킹 헬퍼 메서드에 @Transactional을 적용하고 코루틴 컨텍스트에서 호출한다
    override suspend fun saveReviewResult(
        reviewRequestId: Long,
        review: CodeReview,
        modelName: String?,
    ): Unit = withContext(Dispatchers.IO) {
        saveReviewResultSync(reviewRequestId, review, modelName)
    }

    @Transactional
    private fun saveReviewResultSync(
        reviewRequestId: Long,
        review: CodeReview,
        modelName: String?,
    ) {
        val issuesJson = objectMapper.writeValueAsString(review.issues)
        val saved = reviewResultRepository.save(
            ReviewResultEntity(
                reviewRequestId = reviewRequestId,
                summary = review.summary,
                issuesJson = issuesJson,
                modelName = modelName,
                toolCallCount = review.toolCallCount,
            ),
        )
        // 이슈 수만큼 카테고리 행 저장 — GROUP BY 통계 집계용
        val categories = review.issues.map { issue ->
            ReviewIssueCategoryEntity(
                reviewResultId = saved.id,
                category = issue.category,
            )
        }
        if (categories.isNotEmpty()) {
            reviewIssueCategoryRepository.saveAll(categories)
        }
    }

    // Tool 호출 이력 저장은 추후 ToolCallLogger 연동으로 구현 예정
    override suspend fun saveToolCallLog(
        reviewRequestId: Long,
        toolName: String,
        argumentsJson: String?,
        responseSize: Int?,
        elapsedMs: Int,
        success: Boolean,
    ) = Unit
}
