package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewIssueCategoryEntity

interface ReviewIssueCategoryRepository : JpaRepository<ReviewIssueCategoryEntity, Long> {

    // 카테고리 통계 집계용 — chunk 단위 전체 조회
    fun findAllBy(pageable: Pageable): Page<ReviewIssueCategoryEntity>

    // 특정 리뷰 결과의 이슈 수 조회
    fun countByReviewResultId(reviewResultId: Long): Long
}
