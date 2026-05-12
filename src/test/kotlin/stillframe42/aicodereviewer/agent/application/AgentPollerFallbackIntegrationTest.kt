package stillframe42.aicodereviewer.agent.application

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import stillframe42.aicodereviewer.config.GitHubProperties
import stillframe42.aicodereviewer.github.adapter.`in`.web.computeSignature
import stillframe42.aicodereviewer.github.adapter.out.persistence.ProcessedPullRequestEventRepository
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ToolCallLogRepository
import java.util.concurrent.TimeUnit.SECONDS

// 폴러 timeout/failed → runAiReview catch → DefaultReviewService fallback 종단 검증
// AgentFallbackIntegrationTest 는 requestDeepAnalysis 자체 실패만 다루므로, 폴링 단계 실패는 별도 검증
class AgentPollerFallbackIntegrationTest : AbstractIntegrationTest() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun pollOverrides(registry: DynamicPropertyRegistry) {
            // polling timeout 빠르게 — max-attempts 3회 × interval 10ms = 30ms 안에 timeout 도달
            registry.add("agent.python.poll.interval") { "10ms" }
            registry.add("agent.python.poll.max-attempts") { "3" }
            registry.add("agent.python.poll.timeout") { "5s" }
        }
    }

    @Autowired
    private lateinit var properties: GitHubProperties

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

    @AfterEach
    fun cleanDb() {
        reviewIssueCategoryRepository.deleteAll()
        toolCallLogRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
        processedEventRepository.deleteAll()
    }

    @Test
    fun `보안 PR 에서 polling 모두 PROCESSING 이면 max-attempts timeout 후 Spring AI 로 fallback`() {
        val installationId = 55555555L
        val repo = "owner/repo-poll-timeout"
        val prNumber = 501
        val headSha = "ptm111abc"
        val analysisId = "poll-timeout-stub"

        WireMockStubs.stubInstallationToken(wireMock, installationId)
        WireMockStubs.stubPrDiff(wireMock, repo, prNumber, AnthropicResponseFixtures.SIMPLE_DIFF)
        stubPrFilesWithSecurity(repo, prNumber)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        WireMockStubs.stubAgentAnalyzeAccepted(wireMock, analysisId)
        WireMockStubs.stubAgentPollSequence(
            wireMock = wireMock,
            analysisId = analysisId,
            statuses = List(3) { "PROCESSING" },
        )
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, repo, prNumber, reviewId = 9904L)

        postWebhook(repo, prNumber, headSha, installationId)

        // fallback 결과 등록 1건 + agent 마커 부재
        await.atMost(15, SECONDS).untilAsserted {
            wireMock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/repos/$repo/pulls/$prNumber/reviews")),
            )
        }
        wireMock.verify(
            0,
            postRequestedFor(urlPathEqualTo("/repos/$repo/pulls/$prNumber/reviews"))
                .withRequestBody(containing("Python 에이전트 심층 분석 결과")),
        )
    }

    @Test
    fun `보안 PR 에서 polling FAILED 응답이면 도메인 예외 후 Spring AI 로 fallback`() {
        val installationId = 66666666L
        val repo = "owner/repo-poll-failed"
        val prNumber = 601
        val headSha = "pfd222abc"
        val analysisId = "poll-failed-stub"

        WireMockStubs.stubInstallationToken(wireMock, installationId)
        WireMockStubs.stubPrDiff(wireMock, repo, prNumber, AnthropicResponseFixtures.SIMPLE_DIFF)
        stubPrFilesWithSecurity(repo, prNumber)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        WireMockStubs.stubAgentAnalyzeAccepted(wireMock, analysisId)
        WireMockStubs.stubAgentPollSequence(
            wireMock = wireMock,
            analysisId = analysisId,
            statuses = listOf("FAILED"),
            finalError = "model error",
        )
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, repo, prNumber, reviewId = 9905L)

        postWebhook(repo, prNumber, headSha, installationId)

        await.atMost(15, SECONDS).untilAsserted {
            wireMock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/repos/$repo/pulls/$prNumber/reviews")),
            )
        }
        wireMock.verify(
            0,
            postRequestedFor(urlPathEqualTo("/repos/$repo/pulls/$prNumber/reviews"))
                .withRequestBody(containing("Python 에이전트 심층 분석 결과")),
        )
    }

    private fun stubPrFilesWithSecurity(repo: String, prNumber: Int) {
        val (owner, repoName) = repo.split("/", limit = 2)
        wireMock.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber/files"))
                .willReturn(
                    okJson(
                        """[{"filename":"src/main/kotlin/SecurityConfig.kt","changes":5,"status":"modified","additions":5,"deletions":0}]""",
                    ),
                ),
        )
    }

    private fun postWebhook(
        repo: String,
        prNumber: Int,
        headSha: String,
        installationId: Long,
    ) {
        val payload = buildPayload(repo, prNumber, headSha, installationId)
        val signature = "sha256=${computeSignature(payload.toByteArray(Charsets.UTF_8), properties.app.webhookSecret)}"

        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", signature)
            .header("X-GitHub-Event", "pull_request")
            .body(payload)
            .exchange()
            .expectStatus().isEqualTo(202)
    }

    private fun buildPayload(
        repo: String,
        prNumber: Int,
        headSha: String,
        installationId: Long,
    ): String = """
        {
          "action": "opened",
          "installation": { "id": $installationId },
          "repository": { "full_name": "$repo" },
          "pull_request": {
            "number": $prNumber,
            "head": { "sha": "$headSha" },
            "title": "test: agent poll fallback",
            "user": { "login": "tester" }
          }
        }
    """.trimIndent()
}
