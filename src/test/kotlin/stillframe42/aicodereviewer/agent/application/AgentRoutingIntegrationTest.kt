package stillframe42.aicodereviewer.agent.application

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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

// Agent 라우팅 통합 테스트
// - 보안 파일 포함 PR → AgentReviewService 경로 (agent stub 마커 포함)
// - 일반 파일만 포함 PR → DefaultReviewService 경로 (agent stub 마커 없음)
class AgentRoutingIntegrationTest : AbstractIntegrationTest() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun pollOverrides(registry: DynamicPropertyRegistry) {
            registry.add("agent.remote.poll.interval") { "10ms" }
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
    fun `보안 파일 포함 PR - agent 경로로 라우팅되어 STUB finding 이 PR 코멘트에 포함된다`() {
        val installationId = 22222222L
        val repo = "owner/repo-sec"
        val prNumber = 201
        val headSha = "sec111abc"

        val analysisId = "agent-analysis-stub"
        WireMockStubs.stubInstallationToken(wireMock, installationId)
        WireMockStubs.stubPrDiff(wireMock, repo, prNumber, AnthropicResponseFixtures.SIMPLE_DIFF)
        stubPrFilesWithSecurity(repo, prNumber)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        WireMockStubs.stubAgentAnalyzeAccepted(wireMock, analysisId)
        WireMockStubs.stubAgentPollSequence(
            wireMock = wireMock,
            analysisId = analysisId,
            statuses = listOf("DONE"),
            finalIssuesJson = """[{"severity":"HIGH","type":"SECURITY","location":"src/main/kotlin/SecurityConfig.kt:1","description":"라우팅 회귀 검증 finding","suggestion":"WireMock 스텁 응답"}]""",
        )
        WireMockStubs.stubPostPrReview(wireMock, repo, prNumber, reviewId = 9901L)

        postWebhook(repo, prNumber, headSha, installationId)

        // Agent 경로 응답: summary = "에이전트 심층 분석 결과 ...",
        //                  issue description = "라우팅 회귀 검증 finding" (실어댑터 → WireMock)
        await.atMost(15, SECONDS).untilAsserted {
            wireMock.verify(
                postRequestedFor(urlPathEqualTo("/repos/$repo/pulls/$prNumber/reviews"))
                    .withRequestBody(containing("에이전트 심층 분석 결과"))
                    .withRequestBody(containing("라우팅 회귀 검증 finding")),
            )
        }
    }

    @Test
    fun `일반 파일만 포함 PR - DefaultReviewService 경로로 라우팅되고 agent 마커가 없다`() {
        val installationId = 33333333L
        val repo = "owner/repo-normal"
        val prNumber = 301
        val headSha = "nor222abc"

        WireMockStubs.stubInstallationToken(wireMock, installationId)
        WireMockStubs.stubPrDiff(wireMock, repo, prNumber, AnthropicResponseFixtures.SIMPLE_DIFF)
        stubPrFilesWithController(repo, prNumber)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, repo, prNumber, reviewId = 9902L)

        postWebhook(repo, prNumber, headSha, installationId)

        // DefaultReviewService 경로 응답에는 agent 마커가 없어야 한다
        await.atMost(15, SECONDS).untilAsserted {
            wireMock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/repos/$repo/pulls/$prNumber/reviews")),
            )
        }
        // agent 마커가 포함된 리뷰 등록 요청이 없어야 한다
        wireMock.verify(
            0,
            postRequestedFor(urlPathEqualTo("/repos/$repo/pulls/$prNumber/reviews"))
                .withRequestBody(containing("에이전트 심층 분석 결과")),
        )
        // 일반 PR 은 원격 에이전트 호출 0회 (POST analyze + GET polling 모두)
        wireMock.verify(
            0,
            postRequestedFor(urlPathEqualTo("/agent/analyze")),
        )
        wireMock.verify(
            0,
            getRequestedFor(urlPathMatching("/agent/analyze/.*")),
        )
    }

    // SecurityConfig.kt → securityKeywords 에 "security" 포함 → SECURITY 카테고리 → agent 경로
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

    // UserController.kt → "controller" 포함 → API 카테고리 → agent 경로 아님
    private fun stubPrFilesWithController(repo: String, prNumber: Int) {
        val (owner, repoName) = repo.split("/", limit = 2)
        wireMock.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber/files"))
                .willReturn(
                    okJson(
                        """[{"filename":"src/main/kotlin/UserController.kt","changes":3,"status":"modified","additions":3,"deletions":0}]""",
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
            "title": "test: agent routing",
            "user": { "login": "tester" }
          }
        }
    """.trimIndent()
}
