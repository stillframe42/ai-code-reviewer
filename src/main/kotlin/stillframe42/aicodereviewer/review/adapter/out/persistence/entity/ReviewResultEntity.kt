package stillframe42.aicodereviewer.review.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// 리뷰 결과 JPA Entity — review_results 테이블에 매핑
// reviewRequestId를 FK Long으로 보유 (불필요한 JOIN 방지, 조회는 Repository 통해 처리)
@Entity
@Table(name = "review_results")
class ReviewResultEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "review_request_id", nullable = false)
    val reviewRequestId: Long,

    @Column(name = "summary", columnDefinition = "TEXT")
    val summary: String? = null,

    // CodeIssue 목록을 JSON 직렬화하여 저장 — 역직렬화는 Phase 6 어댑터에서 처리
    @Column(name = "issues_json", columnDefinition = "TEXT")
    val issuesJson: String? = null,

    @Column(name = "model_name", length = 100)
    val modelName: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
