package stillframe42.aicodereviewer.github.adapter.out.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.port.out.ProcessedEventPort
import java.time.Instant

// ProcessedEventPort 구현체 — JPA를 통해 처리 이력을 H2/PostgreSQL에 저장한다
// JPA는 블로킹 API이므로 Dispatchers.IO 컨텍스트에서 실행한다
@Component
class ProcessedEventAdapter(
    private val repository: ProcessedPullRequestEventRepository,
) : ProcessedEventPort {

    override suspend fun isAlreadyProcessed(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
    ): Boolean = withContext(Dispatchers.IO) {
        repository.existsByRepositoryFullNameAndPullRequestNumberAndHeadSha(
            repositoryFullName = repositoryFullName,
            pullRequestNumber = pullRequestNumber,
            headSha = headSha,
        )
    }

    override suspend fun markAsProcessed(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
    ): Unit = withContext(Dispatchers.IO) {
        repository.save(
            ProcessedPullRequestEventEntity(
                repositoryFullName = repositoryFullName,
                pullRequestNumber = pullRequestNumber,
                headSha = headSha,
                processedAt = Instant.now(),
            ),
        )
    }
}
