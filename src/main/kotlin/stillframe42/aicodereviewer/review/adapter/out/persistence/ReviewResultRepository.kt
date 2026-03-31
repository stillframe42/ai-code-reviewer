package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewResultEntity

interface ReviewResultRepository : JpaRepository<ReviewResultEntity, Long> {

    // 리뷰 요청 ID로 결과 조회
    fun findByReviewRequestId(reviewRequestId: Long): ReviewResultEntity?

    // 전체 리뷰의 평균 Tool 호출 횟수 — 결과 없으면 0.0 반환
    @Query("SELECT COALESCE(AVG(r.toolCallCount), 0.0) FROM ReviewResultEntity r")
    fun averageToolCallCount(): Double
}
