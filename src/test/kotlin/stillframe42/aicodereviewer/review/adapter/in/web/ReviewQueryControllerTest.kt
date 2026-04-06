package stillframe42.aicodereviewer.review.adapter.`in`.web

import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.adapter.out.persistence.LlmCostLogRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewPersistenceAdapter
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewStatsResponse
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewSummaryResponse
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus

// ReviewQueryController 통합 테스트 — RANDOM_PORT 실제 서버에 RestTestClient로 검증합니다.
class ReviewQueryControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var persistenceAdapter: ReviewPersistenceAdapter

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
        // 다른 테스트가 남긴 데이터를 초기화 (FK 순서: 자식 먼저 삭제)
        llmCostLogRepository.deleteAll()
        reviewIssueCategoryRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        // FK 순서: 자식 먼저 삭제
        llmCostLogRepository.deleteAll()
        reviewIssueCategoryRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @Test
    fun `리뷰가 존재하는 PR 조회 시 200과 요약 정보를 반환한다`() = runTest {
        // 리뷰 요청 저장
        val requestId = persistenceAdapter.saveReviewRequest("owner/repo", 42, "abc123")

        // 리뷰 결과 저장
        val review = CodeReview(
            overallScore = 8,
            summary = "양호한 코드입니다.",
            issues = listOf(
                CodeIssue(
                    id = "i1",
                    category = IssueCategory.SECURITY,
                    line = 5,
                    severity = IssueSeverity.MAJOR,
                    description = "보안 이슈",
                    suggestion = "수정 필요",
                ),
            ),
            positives = listOf("명확한 네이밍"),
            toolCallCount = 2,
        )
        persistenceAdapter.saveReviewResult(requestId, review, "claude-sonnet-4-6")

        // 상태 업데이트
        persistenceAdapter.updateReviewStatus(requestId, ReviewRequestStatus.DONE)

        // GET 요청 및 검증
        val body = client.get().uri("/api/reviews/owner/repo/42")
            .exchange()
            .expectStatus().isOk
            .expectBody(ReviewSummaryResponse::class.java)
            .returnResult().responseBody!!

        assertEquals("owner/repo", body.repoFullName)
        assertEquals(42, body.prNumber)
        assertEquals("양호한 코드입니다.", body.summary)
        assertEquals(1, body.issueCount)
        assertEquals(2, body.toolCallCount)
        assertEquals(ReviewRequestStatus.DONE, body.status)
    }

    @Test
    fun `리뷰가 없는 PR 조회 시 404를 반환한다`() {
        client.get().uri("/api/reviews/owner/repo/999")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `stats 엔드포인트는 집계 결과를 반환한다`() = runTest {
        // 첫 번째 리뷰: SECURITY 이슈 2건, toolCallCount=3
        val requestId1 = persistenceAdapter.saveReviewRequest("owner/repo", 100, "sha001")
        val review1 = CodeReview(
            overallScore = 6,
            summary = "보안 이슈가 있습니다.",
            issues = listOf(
                CodeIssue(
                    id = "s1",
                    category = IssueCategory.SECURITY,
                    line = 1,
                    severity = IssueSeverity.CRITICAL,
                    description = "SQL Injection",
                    suggestion = "PreparedStatement 사용",
                ),
                CodeIssue(
                    id = "s2",
                    category = IssueCategory.SECURITY,
                    line = 2,
                    severity = IssueSeverity.MAJOR,
                    description = "XSS 취약점",
                    suggestion = "입력값 이스케이프",
                ),
            ),
            positives = listOf("테스트 커버리지 양호"),
            toolCallCount = 3,
        )
        persistenceAdapter.saveReviewResult(requestId1, review1, "claude-sonnet-4-6")
        persistenceAdapter.updateReviewStatus(requestId1, ReviewRequestStatus.DONE)

        // 두 번째 리뷰: PERFORMANCE 이슈 1건, toolCallCount=1
        val requestId2 = persistenceAdapter.saveReviewRequest("owner/repo", 101, "sha002")
        val review2 = CodeReview(
            overallScore = 8,
            summary = "성능 개선 필요.",
            issues = listOf(
                CodeIssue(
                    id = "p1",
                    category = IssueCategory.PERFORMANCE,
                    line = 10,
                    severity = IssueSeverity.MINOR,
                    description = "N+1 쿼리",
                    suggestion = "fetch join 사용",
                ),
            ),
            positives = listOf("명확한 코드 구조"),
            toolCallCount = 1,
        )
        persistenceAdapter.saveReviewResult(requestId2, review2, "claude-sonnet-4-6")
        persistenceAdapter.updateReviewStatus(requestId2, ReviewRequestStatus.DONE)

        // GET /api/reviews/stats 요청 및 검증
        val body = client.get().uri("/api/reviews/stats")
            .exchange()
            .expectStatus().isOk
            .expectBody(ReviewStatsResponse::class.java)
            .returnResult().responseBody!!

        assertEquals(2L, body.totalReviews)
        assertEquals(2L, body.categoryDistribution[IssueCategory.SECURITY])
        assertEquals(1L, body.categoryDistribution[IssueCategory.PERFORMANCE])
        assertEquals(0L, body.categoryDistribution[IssueCategory.READABILITY])
        assertEquals(2.0, body.averageToolCallCount, 0.01)
        // LLM 호출 이력 없으므로 기본값 검증
        assertEquals(emptyMap<String, BigDecimal>(), body.costByModel)
        assertEquals(0.0, body.cacheHitRate, 0.001)
        assertEquals(BigDecimal.ZERO.setScale(0), body.estimatedSavings.setScale(0))
    }

    @Test
    fun `stats 엔드포인트는 LLM 호출 이력이 없을 때 기본값을 반환한다`() = runTest {
        // 리뷰 데이터 없이 바로 GET /api/reviews/stats 호출
        val body = client.get().uri("/api/reviews/stats")
            .exchange()
            .expectStatus().isOk
            .expectBody(ReviewStatsResponse::class.java)
            .returnResult().responseBody!!

        assertEquals(0L, body.totalReviews)
        assertEquals(emptyMap<String, BigDecimal>(), body.costByModel)
        assertEquals(0.0, body.cacheHitRate, 0.001)
        assertEquals(BigDecimal.ZERO.setScale(0), body.estimatedSavings.setScale(0))
    }
}
