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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.config.GitHubProperties
import stillframe42.aicodereviewer.github.adapter.`in`.web.computeSignature
import stillframe42.aicodereviewer.github.adapter.out.persistence.ProcessedPullRequestEventRepository
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.review.adapter.out.persistence.ToolCallLogRepository
import java.util.concurrent.TimeUnit.SECONDS

// Agent 실패 fallback 통합 테스트
// AgentAnalysisPort 가 예외를 던질 때 DefaultReviewService 로 fallback 되어 일반 리뷰가 등록되는지 검증한다
@Import(FailingAgentConfig::class)
class AgentFallbackIntegrationTest : AbstractIntegrationTest() {

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
    fun `보안 파일 PR 에서 agent 가 실패하면 DefaultReviewService 로 fallback 되어 일반 리뷰가 등록된다`() {
        val installationId = 44444444L
        val repo = "owner/repo-fallback"
        val prNumber = 401
        val headSha = "fall333abc"

        WireMockStubs.stubInstallationToken(wireMock, installationId)
        WireMockStubs.stubPrDiff(wireMock, repo, prNumber, AnthropicResponseFixtures.SIMPLE_DIFF)
        stubPrFilesWithSecurity(repo, prNumber)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, repo, prNumber, reviewId = 9903L)

        postWebhook(repo, prNumber, headSha, installationId)

        // agent 실패 후 fallback → DefaultReviewService 결과로 리뷰 등록, agent 마커 없어야 한다
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
            "title": "test: agent fallback",
            "user": { "login": "tester" }
          }
        }
    """.trimIndent()
}

@TestConfiguration
class FailingAgentConfig {

    @Bean
    @Primary
    fun failingAgentAnalysisPort(): AgentAnalysisPort = object : AgentAnalysisPort {
        override suspend fun requestDeepAnalysis(command: AgentAnalysisCommand): AgentAnalysisResult =
            throw AgentUnavailableException("simulated agent down")

        override suspend fun getAnalysisResult(analysisId: String): AgentAnalysisResult =
            error("not used")

        override suspend fun checkHealth(): Boolean = false
    }
}
