package stillframe42.aicodereviewer.review.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import stillframe42.aicodereviewer.review.domain.model.IssueCategory

// 리뷰 결과별 이슈 카테고리 Entity — review_issue_categories 테이블에 매핑
// 이슈 1건당 1행 삽입하여 카테고리별 통계 집계에 사용한다
@Entity
@Table(name = "review_issue_categories")
class ReviewIssueCategoryEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "review_result_id", nullable = false)
    val reviewResultId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    val category: IssueCategory,
)
