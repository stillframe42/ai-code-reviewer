package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewResultEntity

interface ReviewResultRepository : JpaRepository<ReviewResultEntity, Long> {

    // 리뷰 요청 ID로 결과 조회
    fun findByReviewRequestId(reviewRequestId: Long): ReviewResultEntity?

    // 평균 Tool 호출 횟수 집계용 — chunk 단위 전체 조회
    fun findAllBy(pageable: Pageable): Page<ReviewResultEntity>
}
