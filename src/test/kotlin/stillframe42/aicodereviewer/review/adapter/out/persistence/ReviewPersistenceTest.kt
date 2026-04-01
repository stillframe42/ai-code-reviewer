package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewRequestEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewResultEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ToolCallLogEntity
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus

// ReviewRequest / ReviewResult / ToolCallLog Entity 저장·조회 통합 테스트 — PostgreSQL Testcontainers 사용
class ReviewPersistenceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reviewRequestRepository: ReviewRequestRepository

    @Autowired
    private lateinit var reviewResultRepository: ReviewResultRepository

    @Autowired
    private lateinit var toolCallLogRepository: ToolCallLogRepository

    @AfterEach
    fun tearDown() {
        // FK 제약으로 인해 자식 테이블 먼저 삭제
        toolCallLogRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @Test
    fun `ReviewRequestEntity 저장 후 동일 상태로 조회된다`() {
        val entity = ReviewRequestEntity(
            repoFullName = "owner/repo",
            prNumber = 42,
            headSha = "abc1234567890",
            status = ReviewRequestStatus.PENDING,
        )
        val saved = reviewRequestRepository.save(entity)

        val found = reviewRequestRepository.findById(saved.id).orElseThrow()
        assertEquals("owner/repo", found.repoFullName)
        assertEquals(42, found.prNumber)
        assertEquals(ReviewRequestStatus.PENDING, found.status)
    }

    @Test
    fun `ReviewResultEntity를 review_request_id로 연결하여 저장한다`() {
        val request = reviewRequestRepository.save(
            ReviewRequestEntity(
                repoFullName = "owner/repo",
                prNumber = 1,
                headSha = "sha001",
                status = ReviewRequestStatus.DONE,
            ),
        )
        val result = reviewResultRepository.save(
            ReviewResultEntity(
                reviewRequestId = request.id,
                summary = "전반적으로 코드 품질이 양호합니다.",
                issuesJson = """[{"id":"1","severity":"LOW"}]""",
                modelName = "claude-haiku-4-5-20251001",
            ),
        )

        val found = reviewResultRepository.findById(result.id).orElseThrow()
        assertEquals(request.id, found.reviewRequestId)
        assertEquals("전반적으로 코드 품질이 양호합니다.", found.summary)
        assertEquals("claude-haiku-4-5-20251001", found.modelName)
    }

    @Test
    fun `ToolCallLogEntity를 review_request_id로 연결하여 저장한다`() {
        val request = reviewRequestRepository.save(
            ReviewRequestEntity(
                repoFullName = "owner/repo",
                prNumber = 2,
                headSha = "sha002",
                status = ReviewRequestStatus.PROCESSING,
            ),
        )
        val log = toolCallLogRepository.save(
            ToolCallLogEntity(
                reviewRequestId = request.id,
                toolName = "getFileContent",
                argumentsJson = """{"path":"src/Foo.kt"}""",
                responseSize = 512,
                elapsedMs = 150,
                success = true,
            ),
        )

        val found = toolCallLogRepository.findById(log.id).orElseThrow()
        assertEquals("getFileContent", found.toolName)
        assertEquals(150, found.elapsedMs)
        assertTrue(found.success)
    }
}
