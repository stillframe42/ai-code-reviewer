package stillframe42.aicodereviewer.github.adapter.out.persistence

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

// ProcessedEventAdapter 통합 테스트 — H2 인메모리 DB로 중복 처리 방지 시나리오를 검증한다
@SpringBootTest
class ProcessedEventAdapterTest {

    @Autowired
    private lateinit var adapter: ProcessedEventAdapter

    @Autowired
    private lateinit var repository: ProcessedPullRequestEventRepository

    @AfterEach
    fun tearDown() {
        repository.deleteAll()
    }

    @Test
    fun `markAsProcessed 전에는 isAlreadyProcessed가 false를 반환한다`() = runBlocking {
        val result = adapter.isAlreadyProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
        )

        assertFalse(result)
    }

    @Test
    fun `markAsProcessed 후에는 isAlreadyProcessed가 true를 반환한다`() = runBlocking {
        adapter.markAsProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
        )

        val result = adapter.isAlreadyProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
        )

        assertTrue(result)
    }

    @Test
    fun `다른 SHA는 중복으로 간주하지 않는다`() = runBlocking {
        adapter.markAsProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
        )

        val result = adapter.isAlreadyProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "def456",
        )

        assertFalse(result)
    }

    @Test
    fun `동일 조합을 두 번 markAsProcessed하면 DataIntegrityViolationException이 발생한다`() {
        repository.saveAndFlush(
            ProcessedPullRequestEventEntity(
                repositoryFullName = "owner/repo",
                pullRequestNumber = 42,
                headSha = "abc123",
            ),
        )

        assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(
                ProcessedPullRequestEventEntity(
                    repositoryFullName = "owner/repo",
                    pullRequestNumber = 42,
                    headSha = "abc123",
                ),
            )
        }
    }
}
