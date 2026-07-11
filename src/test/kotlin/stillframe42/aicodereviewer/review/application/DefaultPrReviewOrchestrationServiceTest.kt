package stillframe42.aicodereviewer.review.application

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrFileStatus
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ToolCallLogRepository
import stillframe42.aicodereviewer.review.domain.model.PrReviewCommand
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import stillframe42.aicodereviewer.review.domain.port.`in`.PrReviewOrchestrationUseCase

// DefaultPrReviewOrchestrationService 통합 테스트 — 외부 API 는 WireMock 으로 모킹한다
class DefaultPrReviewOrchestrationServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var orchestration: PrReviewOrchestrationUseCase

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var reviewRequestRepository: ReviewRequestRepository

    @Autowired
    private lateinit var reviewResultRepository: ReviewResultRepository

    @Autowired
    private lateinit var reviewIssueCategoryRepository: ReviewIssueCategoryRepository

    @Autowired
    private lateinit var toolCallLogRepository: ToolCallLogRepository

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubAnyInstallationToken(wireMock)
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
    }

    @AfterEach
    fun cleanDb() {
        // FK 순서: 자식 테이블부터 삭제
        reviewIssueCategoryRepository.deleteAll()
        toolCallLogRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
    }

    @Test
    fun `일반 PR 은 Spring AI 경로로 리뷰하고 DONE 상태로 저장한다`(): Unit = runBlocking {
        val review = orchestration.orchestrate(command(prNumber = 42, prFiles = listOf(normalPrFile())))

        assertThat(review).isNotNull
        val request = latestRequest(42)
        assertThat(request.status).isEqualTo(ReviewRequestStatus.DONE)
        assertThat(request.completedAt).isNotNull()
        assertThat(reviewResultRepository.findAll()).hasSize(1)
    }

    @Test
    fun `AI 호출이 실패하면 null 을 반환하고 FAILED 상태로 저장한다`(): Unit = runBlocking {
        WireMockStubs.stubAnthropicError(wireMock)

        val review = orchestration.orchestrate(command(prNumber = 43, prFiles = listOf(normalPrFile())))

        assertThat(review).isNull()
        assertThat(latestRequest(43).status).isEqualTo(ReviewRequestStatus.FAILED)
    }

    @Test
    fun `보안 파일 PR 은 agent 경로로 리뷰한다`(): Unit = runBlocking {
        stubRemoteAgentDone()

        val review = orchestration.orchestrate(command(prNumber = 44, prFiles = listOf(securityPrFile())))

        assertThat(review).isNotNull
        wireMock.verify(postRequestedFor(urlPathEqualTo("/agent/analyze")))
        assertThat(latestRequest(44).status).isEqualTo(ReviewRequestStatus.DONE)
    }

    @Test
    fun `agent 가 실패하면 Spring AI 로 폴백하고 폴백 메트릭이 증가한다`(): Unit = runBlocking {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze")).willReturn(aResponse().withStatus(503)),
        )
        val before = fallbackCount("unavailable")

        val review = orchestration.orchestrate(command(prNumber = 45, prFiles = listOf(securityPrFile())))

        assertThat(review).isNotNull
        assertThat(fallbackCount("unavailable")).isEqualTo(before + 1.0)
        assertThat(latestRequest(45).status).isEqualTo(ReviewRequestStatus.DONE)
    }

    private fun command(prNumber: Int, prFiles: List<PrFile>) = PrReviewCommand(
        repositoryFullName = TEST_REPO,
        pullRequestNumber = prNumber,
        headSha = "ORCH-HEAD",
        installationId = WireMockStubs.TEST_INSTALLATION_ID,
        prDiff = AnthropicResponseFixtures.SIMPLE_DIFF,
        prFiles = prFiles,
    )

    private fun normalPrFile() = PrFile(
        filename = "src/Foo.kt",
        status = PrFileStatus.MODIFIED,
        additions = 3,
        deletions = 0,
        changes = 3,
        patch = "@@ -1,1 +1,3 @@",
        previousFilename = null,
    )

    // "filter" 키워드 포함 → SecurityFileDetector 가 SECURITY 카테고리로 분류 (AgentSessionIdPropagationIT 와 같은 픽스처)
    private fun securityPrFile() = PrFile(
        filename = "src/main/kotlin/SecurityFilter.kt",
        status = PrFileStatus.MODIFIED,
        additions = 5,
        deletions = 2,
        changes = 7,
        patch = "@@ -1,2 +1,5 @@",
        previousFilename = null,
    )

    private fun latestRequest(prNumber: Int) =
        requireNotNull(
            reviewRequestRepository.findTopByRepoFullNameAndPrNumberOrderByCreatedAtDesc(TEST_REPO, prNumber),
        )

    private fun fallbackCount(reason: String): Double =
        meterRegistry.find("agent.fallback.count").tag("reason", reason).counter()?.count() ?: 0.0

    // POST /agent/analyze 즉시 DONE, GET /agent/analyze/{id} 도 DONE — 폴러가 1회만에 종료
    private fun stubRemoteAgentDone() {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(okJson("""{"analysis_id":"orch-test","status":"DONE","issues":[]}""")),
        )
        wireMock.stubFor(
            get(urlPathMatching("/agent/analyze/.+"))
                .willReturn(okJson("""{"analysis_id":"orch-test","status":"DONE","issues":[]}""")),
        )
    }

    companion object {
        private const val TEST_REPO = "owner/orchestration-repo"
    }
}
