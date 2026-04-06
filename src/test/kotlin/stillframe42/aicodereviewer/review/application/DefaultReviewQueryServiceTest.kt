package stillframe42.aicodereviewer.review.application

import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.adapter.out.persistence.LlmCostLogRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.LlmCostLogEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewIssueCategoryEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewRequestEntity
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewResultEntity
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus

// DefaultReviewQueryService 통합 테스트 — PostgreSQL Testcontainers 사용
class DefaultReviewQueryServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var service: DefaultReviewQueryService

    @Autowired
    private lateinit var llmCostLogRepository: LlmCostLogRepository

    @Autowired
    private lateinit var reviewIssueCategoryRepository: ReviewIssueCategoryRepository

    @Autowired
    private lateinit var reviewResultRepository: ReviewResultRepository

    @Autowired
    private lateinit var reviewRequestRepository: ReviewRequestRepository

    @AfterEach
    fun tearDown() {
        // FK 제약으로 자식 테이블 먼저 삭제, llm_cost_logs는 독립적이므로 먼저 삭제
        llmCostLogRepository.deleteAll()
        reviewIssueCategoryRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @Test
    fun `getReviewByPr는 존재하는 PR의 리뷰 요약을 반환한다`() = runTest {
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
                toolCallCount = 2,
                modelName = "claude-sonnet-4-6",
            )
        )

        // 이슈 카테고리 1건 저장 (SECURITY)
        reviewIssueCategoryRepository.save(
            ReviewIssueCategoryEntity(reviewResultId = result.id, category = IssueCategory.SECURITY)
        )

        val found = service.getReviewByPr("owner/repo", 42)

        // 조합된 결과가 올바르게 반환되어야 한다
        assertNotNull(found)
        assertEquals("owner/repo", found!!.repoFullName)
        assertEquals(42, found.prNumber)
        assertEquals(ReviewRequestStatus.DONE, found.status)
        assertEquals("테스트 요약", found.summary)
        assertEquals(1, found.issueCount)
        assertEquals(2, found.toolCallCount)
        assertEquals("claude-sonnet-4-6", found.modelName)
    }

    @Test
    fun `getReviewByPr는 존재하지 않는 PR에 대해 null을 반환한다`() = runTest {
        // 저장된 데이터가 없으므로 null이어야 한다
        val result = service.getReviewByPr("nobody/norepo", 999)

        assertNull(result)
    }

    @Test
    fun `getStats는 전체 통계를 반환한다`() = runTest {
        // 리뷰 요청 2건 저장
        val firstRequest = reviewRequestRepository.save(
            ReviewRequestEntity(
                repoFullName = "owner/repo",
                prNumber = 1,
                headSha = "sha1",
                status = ReviewRequestStatus.DONE,
                completedAt = Instant.now(),
            )
        )
        reviewRequestRepository.save(
            ReviewRequestEntity(
                repoFullName = "owner/repo",
                prNumber = 2,
                headSha = "sha2",
                status = ReviewRequestStatus.PENDING,
            )
        )

        // 첫 번째 요청에 연결된 리뷰 결과 저장
        val result = reviewResultRepository.save(
            ReviewResultEntity(
                reviewRequestId = firstRequest.id,
                summary = "요약",
                toolCallCount = 4,
                modelName = "claude-sonnet-4-6",
            )
        )

        // 이슈 카테고리 1건 저장 (SECURITY)
        reviewIssueCategoryRepository.save(
            ReviewIssueCategoryEntity(reviewResultId = result.id, category = IssueCategory.SECURITY)
        )

        val stats = service.getStats()

        // 전체 리뷰 수는 2이어야 한다
        assertEquals(2L, stats.totalReviews)
        // 4개 카테고리 모두 포함되어야 한다
        assertEquals(IssueCategory.entries.size, stats.categoryDistribution.size)
        // SECURITY는 1, PERFORMANCE는 0이어야 한다
        assertEquals(1L, stats.categoryDistribution[IssueCategory.SECURITY])
        assertEquals(0L, stats.categoryDistribution[IssueCategory.PERFORMANCE])
        // 평균 툴 호출 횟수는 4.0이어야 한다
        assertEquals(4.0, stats.averageToolCallCount)
    }

    @Test
    fun `getStats는 데이터가 없을 때 기본값을 반환한다`() = runTest {
        // 저장된 데이터 없음 — tearDown 직후 상태와 동일
        val stats = service.getStats()

        assertEquals(0L, stats.totalReviews)
        assertEquals(0.0, stats.averageToolCallCount)
    }

    @Test
    fun `getStats는 비용 및 캐시 통계를 포함한다`() = runTest {
        // LLM 비용 로그 2건 저장 (같은 모델, 각 0.0004 USD)
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

        val stats = service.getStats()

        // 모델별 비용 집계: haiku 2건 = 0.0008 USD
        assertThat(stats.costByModel["claude-haiku-4-5-20251001"])
            .isEqualByComparingTo(BigDecimal("0.000800"))
        // Redis FLUSHALL로 카운터 초기화됨 → cacheHitRate = 0.0
        assertThat(stats.cacheHitRate).isEqualTo(0.0)
        // 캐시 히트 횟수 0 → estimatedSavings = 0
        assertThat(stats.estimatedSavings).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `getStats는 데이터가 없을 때 신규 필드도 기본값을 반환한다`() = runTest {
        val stats = service.getStats()

        assertThat(stats.costByModel).isEmpty()
        assertThat(stats.cacheHitRate).isEqualTo(0.0)
        assertThat(stats.estimatedSavings).isEqualByComparingTo(BigDecimal.ZERO)
    }
}
