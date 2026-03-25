package stillframe42.aicodereviewer.github.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

// 처리된 PR 이벤트 이력 — (레포, PR번호, SHA) 조합의 중복 처리 방지용
@Entity
@Table(
    name = "processed_pull_request_event",
    uniqueConstraints = [
        UniqueConstraint(
            name = "ux_processed_event",
            columnNames = ["repository_full_name", "pull_request_number", "head_sha"],
        ),
    ],
)
class ProcessedPullRequestEventEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "repository_full_name", nullable = false)
    val repositoryFullName: String,

    @Column(name = "pull_request_number", nullable = false)
    val pullRequestNumber: Int,

    @Column(name = "head_sha", nullable = false, length = 40)
    val headSha: String,

    // 등록된 GitHub PR 리뷰 ID — 새 커밋 push 시 dismiss 대상 조회에 사용된다
    @Column(name = "review_id", nullable = false)
    val reviewId: Long,

    @Column(name = "processed_at", nullable = false)
    val processedAt: Instant = Instant.now(),
)
