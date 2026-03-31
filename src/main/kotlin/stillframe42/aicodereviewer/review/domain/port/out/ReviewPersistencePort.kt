package stillframe42.aicodereviewer.review.domain.port.out

import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import java.time.Instant

// 리뷰 저장 아웃바운드 포트 — 쓰기 전용
interface ReviewPersistencePort {

    // 리뷰 요청 저장 (status=PENDING), 생성된 ID 반환
    suspend fun saveReviewRequest(repoFullName: String, prNumber: Int, headSha: String): Long

    // 리뷰 요청 상태 업데이트 — completedAt은 DONE/FAILED 시 전달
    suspend fun updateReviewStatus(id: Long, status: ReviewRequestStatus, completedAt: Instant? = null)

    // 리뷰 결과 저장 — CodeReview를 JSON 직렬화하고 카테고리 행도 함께 삽입
    suspend fun saveReviewResult(reviewRequestId: Long, review: CodeReview, modelName: String?)

    // Tool 호출 이력 저장 — 현 Phase에서는 no-op, 추후 ToolCallLogger 연동으로 확장
    suspend fun saveToolCallLog(
        reviewRequestId: Long,
        toolName: String,
        argumentsJson: String?,
        responseSize: Int?,
        elapsedMs: Int,
        success: Boolean,
    )
}
