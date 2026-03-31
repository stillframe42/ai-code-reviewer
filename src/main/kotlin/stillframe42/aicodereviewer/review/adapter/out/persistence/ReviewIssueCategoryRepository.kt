package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewIssueCategoryEntity
import stillframe42.aicodereviewer.review.domain.model.IssueCategory

// 카테고리별 집계 결과 프로젝션
interface CategoryCount {
    fun getCategory(): IssueCategory
    fun getCount(): Long
}

interface ReviewIssueCategoryRepository : JpaRepository<ReviewIssueCategoryEntity, Long> {

    // 카테고리별 이슈 수 집계 — 전체 리뷰에 걸친 통계
    @Query("SELECT r.category as category, COUNT(r) as count FROM ReviewIssueCategoryEntity r GROUP BY r.category")
    fun countByCategory(): List<CategoryCount>

    // 특정 리뷰 결과의 이슈 수 조회
    fun countByReviewResultId(reviewResultId: Long): Long
}
