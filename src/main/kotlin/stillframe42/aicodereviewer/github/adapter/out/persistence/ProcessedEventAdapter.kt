package stillframe42.aicodereviewer.github.adapter.out.persistence

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.port.out.ProcessedEventPort
import java.time.Instant

// ProcessedEventPort 구현체 — JPA를 통해 처리 이력을 H2/PostgreSQL에 저장한다
@Component
class ProcessedEventAdapter(
    private val repository: ProcessedPullRequestEventRepository,
) : ProcessedEventPort {

    override fun isAlreadyProcessed(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
    ): Boolean =
        repository.existsByRepositoryFullNameAndPullRequestNumberAndHeadSha(
            repositoryFullName = repositoryFullName,
            pullRequestNumber = pullRequestNumber,
            headSha = headSha,
        )

    override fun markAsProcessed(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
        reviewId: Long,
    ) {
        repository.save(
            ProcessedPullRequestEventEntity(
                repositoryFullName = repositoryFullName,
                pullRequestNumber = pullRequestNumber,
                headSha = headSha,
                reviewId = reviewId,
                processedAt = Instant.now(),
            ),
        )
    }

    override fun findLatestReviewId(
        repositoryFullName: String,
        pullRequestNumber: Int,
    ): Long? =
        repository.findTopByRepositoryFullNameAndPullRequestNumberOrderByProcessedAtDesc(
            repositoryFullName = repositoryFullName,
            pullRequestNumber = pullRequestNumber,
        )?.reviewId
}
