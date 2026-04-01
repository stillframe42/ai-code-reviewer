package stillframe42.aicodereviewer.review.adapter.out.persistence

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import java.time.Instant

// ReviewPersistenceAdapter 통합 테스트 — PostgreSQL Testcontainers 사용
class ReviewPersistenceAdapterTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var adapter: ReviewPersistenceAdapter

    @Autowired
    private lateinit var reviewIssueCategoryRepository: ReviewIssueCategoryRepository

    @Autowired
    private lateinit var reviewResultRepository: ReviewResultRepository

    @Autowired
    private lateinit var reviewRequestRepository: ReviewRequestRepository

    @AfterEach
    fun tearDown() {
        // FK 제약으로 자식 테이블 먼저 삭제
        reviewIssueCategoryRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @Test
    fun `saveReviewRequest는 PENDING 상태로 저장하고 ID를 반환한다`() = runTest {
        val id = adapter.saveReviewRequest(
            repoFullName = "owner/repo",
            prNumber = 10,
            headSha = "abc123",
        )

        val entity = reviewRequestRepository.findById(id).orElseThrow()
        assertEquals("owner/repo", entity.repoFullName)
        assertEquals(10, entity.prNumber)
        assertEquals(ReviewRequestStatus.PENDING, entity.status)
        assertNull(entity.completedAt)
    }

    @Test
    fun `updateReviewStatus는 상태와 completedAt을 변경한다`() = runTest {
        val id = adapter.saveReviewRequest("owner/repo", 11, "def456")
        adapter.updateReviewStatus(id, ReviewRequestStatus.DONE, Instant.now())

        val entity = reviewRequestRepository.findById(id).orElseThrow()
        assertEquals(ReviewRequestStatus.DONE, entity.status)
        assertNotNull(entity.completedAt)
    }

    @Test
    fun `saveReviewResult는 ReviewResultEntity와 카테고리 행을 저장한다`() = runTest {
        val requestId = adapter.saveReviewRequest("owner/repo", 12, "ghi789")
        val review = CodeReview(
            overallScore = 7,
            summary = "전반적으로 양호합니다.",
            issues = listOf(
                CodeIssue(
                    id = "i1",
                    category = IssueCategory.SECURITY,
                    line = 10,
                    severity = IssueSeverity.MAJOR,
                    description = "SQL Injection",
                    suggestion = "PreparedStatement 사용",
                ),
                CodeIssue(
                    id = "i2",
                    category = IssueCategory.PERFORMANCE,
                    line = null,
                    severity = IssueSeverity.MINOR,
                    description = "N+1 쿼리",
                    suggestion = "fetch join 사용",
                ),
            ),
            positives = listOf("명확한 변수명"),
            toolCallCount = 3,
        )

        adapter.saveReviewResult(requestId, review, "claude-sonnet-4-6")

        val results = reviewResultRepository.findAll()
        assertEquals(1, results.size)
        val result = results.first()
        assertEquals("전반적으로 양호합니다.", result.summary)
        assertEquals(3, result.toolCallCount)
        assertEquals("claude-sonnet-4-6", result.modelName)

        // 이슈 수만큼 카테고리 행이 저장된다
        val categories = reviewIssueCategoryRepository.findAll()
        assertEquals(2, categories.size)
        assertEquals(1, categories.count { it.category == IssueCategory.SECURITY })
        assertEquals(1, categories.count { it.category == IssueCategory.PERFORMANCE })
    }

    @Test
    fun `saveToolCallLog은 예외 없이 완료된다`() = runTest {
        // Tool 호출 로그 저장은 현재 no-op 구현이므로 예외 없이 완료되어야 한다
        adapter.saveToolCallLog(
            reviewRequestId = 999L,
            toolName = "getFileDiff",
            argumentsJson = """{"path": "src/Main.kt"}""",
            responseSize = 512,
            elapsedMs = 100,
            success = true,
        )
        // 예외가 발생하지 않으면 테스트 통과
    }

    @Test
    fun `updateReviewStatus에서 completedAt이 null이면 기존 값을 유지한다`() = runTest {
        val id = adapter.saveReviewRequest("owner/repo", 20, "sha999")
        val originalCompletedAt = Instant.now()
        // 먼저 completedAt을 설정한다
        adapter.updateReviewStatus(id, ReviewRequestStatus.DONE, originalCompletedAt)

        // completedAt=null로 상태만 변경 시 기존 completedAt이 유지되어야 한다
        adapter.updateReviewStatus(id, ReviewRequestStatus.PENDING, null)

        val entity = reviewRequestRepository.findById(id).orElseThrow()
        assertEquals(ReviewRequestStatus.PENDING, entity.status)
        assertNotNull(entity.completedAt)
    }
}
