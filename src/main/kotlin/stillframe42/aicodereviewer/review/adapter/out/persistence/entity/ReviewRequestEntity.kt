package stillframe42.aicodereviewer.review.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import java.time.Instant

// 리뷰 요청 JPA Entity — review_requests 테이블에 매핑
@Entity
@Table(name = "review_requests")
class ReviewRequestEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "repo_full_name", nullable = false, length = 255)
    val repoFullName: String,

    @Column(name = "pr_number", nullable = false)
    val prNumber: Int,

    @Column(name = "head_sha", nullable = false, length = 40)
    val headSha: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: ReviewRequestStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    val completedAt: Instant? = null,
)
