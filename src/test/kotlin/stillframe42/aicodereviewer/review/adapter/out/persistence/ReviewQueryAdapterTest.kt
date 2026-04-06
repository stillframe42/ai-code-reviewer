package stillframe42.aicodereviewer.review.adapter.out.persistence

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.LlmCostLogEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewIssueCategoryEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewRequestEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewResultEntity
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import java.math.BigDecimal
import java.time.Instant

// ReviewQueryAdapter 통합 테스트 — PostgreSQL Testcontainers 사용
class ReviewQueryAdapterTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var adapter: ReviewQueryAdapter

    @Autowired
    private lateinit var reviewIssueCategoryRepository: ReviewIssueCategoryRepository

    @Autowired
    private lateinit var reviewResultRepository: ReviewResultRepository

    @Autowired
    private lateinit var reviewRequestRepository: ReviewRequestRepository

    @Autowired
    private lateinit var llmCostLogRepository: LlmCostLogRepository

    @BeforeEach
    fun setUp() {
        // 다른 테스트가 남긴 데이터를 초기화
        llmCostLogRepository.deleteAll()
        reviewIssueCategoryRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        // FK 제약으로 자식 테이블 먼저 삭제
        llmCostLogRepository.deleteAll()
        reviewIssueCategoryRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @Test
    fun `findLatestByRepoAndPr는 요청과 결과를 조합하여 반환한다`() = runTest {
        // 리뷰 요청 저장
        val request = reviewRequestRepository.save(
            ReviewRequestEntity(
                repoFullName = "owner/repo",
                prNumber = 42,
                headSha = "abc123",
                status = ReviewRequestStatus.DONE,
                completedAt = Instant.now(),
            )
        )

        // 리뷰 결과 저장
        val result = reviewResultRepository.save(
            ReviewResultEntity(
                reviewRequestId = request.id,
                summary = "테스트 요약",
                toolCallCount = 3,
                modelName = "claude-sonnet-4-6",
            )
        )

        // 이슈 카테고리 2건 저장 (SECURITY, PERFORMANCE)
        reviewIssueCategoryRepository.saveAll(
            listOf(
                ReviewIssueCategoryEntity(reviewResultId = result.id, category = IssueCategory.SECURITY),
                ReviewIssueCategoryEntity(reviewResultId = result.id, category = IssueCategory.PERFORMANCE),
            )
        )

        val found = adapter.findLatestByRepoAndPr("owner/repo", 42)

        // 조합된 결과가 올바르게 반환되어야 한다
        assertNotNull(found)
        assertEquals("owner/repo", found!!.repoFullName)
        assertEquals(42, found.prNumber)
        assertEquals(ReviewRequestStatus.DONE, found.status)
        assertEquals("테스트 요약", found.summary)
        assertEquals(2, found.issueCount)
        assertEquals(3, found.toolCallCount)
        assertEquals("claude-sonnet-4-6", found.modelName)
    }

    @Test
    fun `findLatestByRepoAndPr는 데이터가 없으면 null을 반환한다`() = runTest {
        val result = adapter.findLatestByRepoAndPr("nobody/norepo", 999)

        // 저장된 데이터가 없으면 null이어야 한다
        assertNull(result)
    }

    @Test
    fun `countTotalReviews는 저장된 요청 수를 반환한다`() = runTest {
        // 리뷰 요청 3건 저장
        reviewRequestRepository.saveAll(
            listOf(
                ReviewRequestEntity(repoFullName = "owner/repo", prNumber = 1, headSha = "sha1", status = ReviewRequestStatus.PENDING),
                ReviewRequestEntity(repoFullName = "owner/repo", prNumber = 2, headSha = "sha2", status = ReviewRequestStatus.PENDING),
                ReviewRequestEntity(repoFullName = "owner/repo", prNumber = 3, headSha = "sha3", status = ReviewRequestStatus.PENDING),
            )
        )

        assertEquals(3L, adapter.countTotalReviews())
    }

    @Test
    fun `countByCategory는 없는 카테고리를 0으로 포함한다`() = runTest {
        // 요청 → 결과 → 카테고리(SECURITY만) 저장
        val request = reviewRequestRepository.save(
            ReviewRequestEntity(
                repoFullName = "owner/repo",
                prNumber = 10,
                headSha = "sha10",
                status = ReviewRequestStatus.DONE,
                completedAt = Instant.now(),
            )
        )
        val result = reviewResultRepository.save(
            ReviewResultEntity(
                reviewRequestId = request.id,
                summary = "요약",
                toolCallCount = 1,
                modelName = "claude-sonnet-4-6",
            )
        )
        reviewIssueCategoryRepository.save(
            ReviewIssueCategoryEntity(reviewResultId = result.id, category = IssueCategory.SECURITY)
        )

        val counts = adapter.countByCategory()

        // 4개 카테고리 모두 포함되어야 하고, SECURITY만 1이어야 한다
        assertEquals(IssueCategory.entries.size, counts.size)
        assertEquals(1L, counts[IssueCategory.SECURITY])
        assertEquals(0L, counts[IssueCategory.PERFORMANCE])
        assertEquals(0L, counts[IssueCategory.READABILITY])
        assertEquals(0L, counts[IssueCategory.ARCHITECTURE])
    }

    @Test
    fun `averageToolCallCount는 결과가 없으면 0_0을 반환한다`() = runTest {
        // 저장된 결과 없음
        assertEquals(0.0, adapter.averageToolCallCount())
    }

    @Test
    fun `sumCostByModel은 모델별 누적 비용 합계를 반환한다`() = runTest {
        llmCostLogRepository.saveAll(listOf(
            LlmCostLogEntity(
                modelName = "claude-haiku-4-5-20251001",
                promptTokens = 100,
                completionTokens = 80,
                estimatedCostUsd = BigDecimal("0.000400"),
            ),
            LlmCostLogEntity(
                modelName = "claude-haiku-4-5-20251001",
                promptTokens = 100,
                completionTokens = 80,
                estimatedCostUsd = BigDecimal("0.000400"),
            ),
            LlmCostLogEntity(
                modelName = "claude-sonnet-4-6",
                promptTokens = 200,
                completionTokens = 160,
                estimatedCostUsd = BigDecimal("0.003000"),
            ),
        ))

        val result = adapter.sumCostByModel()

        assertThat(result["claude-haiku-4-5-20251001"]).isEqualByComparingTo(BigDecimal("0.000800"))
        assertThat(result["claude-sonnet-4-6"]).isEqualByComparingTo(BigDecimal("0.003000"))
    }

    @Test
    fun `sumCostByModel은 데이터가 없으면 빈 맵을 반환한다`() = runTest {
        assertThat(adapter.sumCostByModel()).isEmpty()
    }

    @Test
    fun `totalLlmCostSummary는 전체 비용 합계와 호출 수를 반환한다`() = runTest {
        llmCostLogRepository.saveAll(listOf(
            LlmCostLogEntity(
                modelName = "claude-haiku-4-5-20251001",
                promptTokens = 100,
                completionTokens = 80,
                estimatedCostUsd = BigDecimal("0.000400"),
            ),
            LlmCostLogEntity(
                modelName = "claude-haiku-4-5-20251001",
                promptTokens = 100,
                completionTokens = 80,
                estimatedCostUsd = BigDecimal("0.000400"),
            ),
        ))

        val summary = adapter.totalLlmCostSummary()

        assertThat(summary.totalCost).isEqualByComparingTo(BigDecimal("0.000800"))
        assertThat(summary.totalCalls).isEqualTo(2L)
    }

    @Test
    fun `totalLlmCostSummary는 데이터가 없으면 ZERO와 0을 반환한다`() = runTest {
        val summary = adapter.totalLlmCostSummary()

        assertThat(summary.totalCost).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summary.totalCalls).isEqualTo(0L)
    }
}
