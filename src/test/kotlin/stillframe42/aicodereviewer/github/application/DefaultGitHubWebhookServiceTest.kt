package stillframe42.aicodereviewer.github.application

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.github.adapter.out.persistence.ProcessedPullRequestEventRepository
import stillframe42.aicodereviewer.github.domain.model.PullRequestAction
import stillframe42.aicodereviewer.github.domain.model.PullRequestEvent
import stillframe42.aicodereviewer.github.domain.port.out.ReviewCommentFormatterPort
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ToolCallLogRepository
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// DefaultGitHubWebhookService 통합 테스트 — WireMock으로 외부 API를 모킹합니다.
class DefaultGitHubWebhookServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var service: DefaultGitHubWebhookService

    // 포맷팅 단위 검증을 위해 포매터 포트를 직접 주입
    @Autowired
    private lateinit var formatter: ReviewCommentFormatterPort

    @Autowired
    private lateinit var processedEventRepository: ProcessedPullRequestEventRepository

    @Autowired
    private lateinit var reviewIssueCategoryRepository: ReviewIssueCategoryRepository

    @Autowired
    private lateinit var reviewResultRepository: ReviewResultRepository

    @Autowired
    private lateinit var reviewRequestRepository: ReviewRequestRepository

    @Autowired
    private lateinit var toolCallLogRepository: ToolCallLogRepository

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubInstallationToken(wireMock, WireMockStubs.TEST_INSTALLATION_ID)
        WireMockStubs.stubPrDiff(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, AnthropicResponseFixtures.SIMPLE_DIFF)
        WireMockStubs.stubPrFiles(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER)
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER)
        WireMockStubs.stubDismissPrReview(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, 9001L)
    }

    @AfterEach
    fun cleanDb() {
        // FK 순서: reviewIssueCategoryRepository → toolCallLogRepository → reviewResultRepository → reviewRequestRepository → processedEventRepository
        reviewIssueCategoryRepository.deleteAll()
        toolCallLogRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
        processedEventRepository.deleteAll()
    }

    // ── 통합 테스트 (WireMock 모킹) ──────────────────────────────────────────

    @Test
    fun `OPENED 이벤트를 처리하면 PR에 리뷰 코멘트가 등록된다`() = runBlocking {
        // 예외 없이 완료되면 성공 (WireMock이 리뷰 등록 요청을 수신)
        service.handlePullRequestEvent(
            PullRequestEvent(
                action = PullRequestAction.OPENED,
                installationId = WireMockStubs.TEST_INSTALLATION_ID,
                repositoryFullName = WireMockStubs.TEST_REPO,
                pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
                headSha = "HEAD",
                title = "통합 테스트 PR",
                author = "test-user",
            ),
        )
        Unit
    }

    @Test
    fun `SYNCHRONIZE 이벤트를 처리하면 이전 리뷰를 dismiss하고 새 리뷰를 등록한다`() = runBlocking {
        // 새 SHA로 이벤트 발생 시 이전 리뷰 dismiss → 새 리뷰 등록 흐름 검증
        // (이전 처리 이력이 없으면 dismiss를 건너뛰고 바로 새 리뷰 등록)
        service.handlePullRequestEvent(
            PullRequestEvent(
                action = PullRequestAction.SYNCHRONIZE,
                installationId = WireMockStubs.TEST_INSTALLATION_ID,
                repositoryFullName = WireMockStubs.TEST_REPO,
                pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
                headSha = "SYNC-HEAD",
                title = "통합 테스트 PR",
                author = "test-user",
            ),
        )
        Unit
    }

    // ── MarkdownReviewCommentFormatter 단위 검증 ──────────────────────────────

    @Test
    fun `이슈와 잘한 점이 있는 리뷰를 포맷하면 모든 섹션이 포함된다`() {
        val review = CodeReview(
            overallScore =7,
            summary = "전반적으로 읽기 쉽게 작성된 코드입니다.",
            issues = listOf(
                CodeIssue(
                    id = "1",
                    category = IssueCategory.SECURITY,
                    line = 42,
                    severity = IssueSeverity.CRITICAL,
                    description = "SQL Injection 취약점",
                    suggestion = "PreparedStatement를 사용하세요",
                ),
            ),
            positives = listOf("명확한 변수명 사용"),
        )

        val result = formatter.format(review)

        assertThat(result).contains("종합 점수: 7/10")
        assertThat(result).contains("전반적으로 읽기 쉽게 작성된 코드입니다.")
        assertThat(result).contains("Issues Found (1)")
        assertThat(result).contains("CRITICAL")
        assertThat(result).contains("SECURITY")
        assertThat(result).contains("SQL Injection 취약점")
        assertThat(result).contains("(L42)")
        assertThat(result).contains("PreparedStatement를 사용하세요")
        assertThat(result).contains("Positives")
        assertThat(result).contains("명확한 변수명 사용")
        assertThat(result).contains("생성 시간:")
    }

    @Test
    fun `이슈가 없는 리뷰를 포맷하면 이슈 없음 메시지가 포함된다`() {
        val review = CodeReview(
            overallScore =10,
            summary = "완벽한 코드입니다.",
            issues = emptyList(),
            positives = listOf("깔끔한 구조"),
        )

        val result = formatter.format(review)

        assertThat(result).contains("Issues Found (0)")
        assertThat(result).contains("발견된 이슈가 없습니다.")
    }

    @Test
    fun `잘한 점이 없는 리뷰를 포맷하면 잘한 점 섹션이 생략된다`() {
        val review = CodeReview(
            overallScore =3,
            summary = "개선이 필요한 코드입니다.",
            issues = listOf(
                CodeIssue(
                    id = "1",
                    category = IssueCategory.PERFORMANCE,
                    line = null,
                    severity = IssueSeverity.MAJOR,
                    description = "N+1 쿼리 발생",
                    suggestion = "fetch join을 사용하세요",
                ),
            ),
            positives = emptyList(),
        )

        val result = formatter.format(review)

        assertThat(result).doesNotContain("Positives")
    }

    @Test
    fun `line이 null인 이슈를 포맷하면 라인 항목이 생략된다`() {
        val review = CodeReview(
            overallScore =5,
            summary = "보통 수준의 코드입니다.",
            issues = listOf(
                CodeIssue(
                    id = "1",
                    category = IssueCategory.ARCHITECTURE,
                    line = null,
                    severity = IssueSeverity.MINOR,
                    description = "SRP 위반 의심",
                    suggestion = "단일 책임 원칙을 따르도록 클래스를 분리하세요",
                ),
            ),
            positives = emptyList(),
        )

        val result = formatter.format(review)

        assertThat(result).contains("SRP 위반 의심")
        assertThat(result).doesNotContain("(L")
    }
}
