package stillframe42.aicodereviewer.github.adapter.out.persistence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// ProcessedEventAdapter 통합 테스트 — PostgreSQL Testcontainers로 중복 처리 방지 시나리오를 검증한다
class ProcessedEventAdapterTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var adapter: ProcessedEventAdapter

    @Autowired
    private lateinit var repository: ProcessedPullRequestEventRepository

    @AfterEach
    fun tearDown() {
        repository.deleteAll()
    }

    @Test
    fun `markAsProcessed 전에는 isAlreadyProcessed가 false를 반환한다`() {
        val result = adapter.isAlreadyProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
        )

        assertFalse(result)
    }

    @Test
    fun `markAsProcessed 후에는 isAlreadyProcessed가 true를 반환한다`() {
        adapter.markAsProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
            reviewId = 100L,
        )

        val result = adapter.isAlreadyProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
        )

        assertTrue(result)
    }

    @Test
    fun `다른 SHA는 중복으로 간주하지 않는다`() {
        adapter.markAsProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
            reviewId = 100L,
        )

        val result = adapter.isAlreadyProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "def456",
        )

        assertFalse(result)
    }

    @Test
    fun `markAsProcessed 후 findLatestReviewId는 저장된 reviewId를 반환한다`() {
        adapter.markAsProcessed(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
            headSha = "abc123",
            reviewId = 999L,
        )

        val reviewId = adapter.findLatestReviewId(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
        )

        org.junit.jupiter.api.Assertions.assertEquals(999L, reviewId)
    }

    @Test
    fun `처리 이력이 없으면 findLatestReviewId는 null을 반환한다`() {
        val reviewId = adapter.findLatestReviewId(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 42,
        )

        org.junit.jupiter.api.Assertions.assertNull(reviewId)
    }

    @Test
    fun `동일 조합을 두 번 markAsProcessed하면 DataIntegrityViolationException이 발생한다`() {
        repository.saveAndFlush(
            ProcessedPullRequestEventEntity(
                repositoryFullName = "owner/repo",
                pullRequestNumber = 42,
                headSha = "abc123",
                reviewId = 100L,
            ),
        )

        assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(
                ProcessedPullRequestEventEntity(
                    repositoryFullName = "owner/repo",
                    pullRequestNumber = 42,
                    headSha = "abc123",
                    reviewId = 101L,
                ),
            )
        }
    }
}
