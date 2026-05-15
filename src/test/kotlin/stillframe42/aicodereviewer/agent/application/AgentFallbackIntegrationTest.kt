package stillframe42.aicodereviewer.agent.application

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.http.Fault
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
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

// 실제 PythonAgentClient + WireMock 으로 3 시나리오 (connection reset / 5xx / IN_PROGRESS timeout) 모두 검증.
// 각 시나리오는 (a) Spring AI 폴백으로 PR 리뷰 1건 등록 (b) agent.fallback.count{reason} 메트릭이 정확한 reason 으로 1 증가.
class AgentFallbackIntegrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var properties: GitHubProperties
    @Autowired private lateinit var meterRegistry: MeterRegistry
    @Autowired private lateinit var processedEventRepository: ProcessedPullRequestEventRepository
    @Autowired private lateinit var reviewIssueCategoryRepository: ReviewIssueCategoryRepository
    @Autowired private lateinit var reviewResultRepository: ReviewResultRepository
    @Autowired private lateinit var reviewRequestRepository: ReviewRequestRepository
    @Autowired private lateinit var toolCallLogRepository: ToolCallLogRepository

    @AfterEach
    fun cleanDb() {
        reviewIssueCategoryRepository.deleteAll()
        toolCallLogRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
        processedEventRepository.deleteAll()
    }

    @Test
    fun `에이전트 connection reset 시 Spring AI 폴백 + reason=unavailable 메트릭이 증가한다`() {
        val fixture = scenarioFixture(seed = 901)
        val baseline = fallbackCount("unavailable")

        stubGitHubAndAi(fixture, reviewId = 9911L)
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )

        postWebhook(fixture)

        await.atMost(15, SECONDS).untilAsserted {
            wireMock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/repos/${fixture.repo}/pulls/${fixture.prNumber}/reviews")),
            )
            assertThat(fallbackCount("unavailable")).isEqualTo(baseline + 1.0)
        }
        wireMock.verify(
            0,
            postRequestedFor(urlPathEqualTo("/repos/${fixture.repo}/pulls/${fixture.prNumber}/reviews"))
                .withRequestBody(containing("Python 에이전트 심층 분석 결과")),
        )
    }

    @Test
    fun `에이전트 5xx 응답 시 Spring AI 폴백 + reason=unavailable 메트릭이 증가한다`() {
        val fixture = scenarioFixture(seed = 902)
        val baseline = fallbackCount("unavailable")

        stubGitHubAndAi(fixture, reviewId = 9912L)
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(aResponse().withStatus(503)),
        )

        postWebhook(fixture)

        await.atMost(15, SECONDS).untilAsserted {
            wireMock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/repos/${fixture.repo}/pulls/${fixture.prNumber}/reviews")),
            )
            assertThat(fallbackCount("unavailable")).isEqualTo(baseline + 1.0)
        }
    }

    @Test
    fun `에이전트 무한 IN_PROGRESS 시 timeout 폴백 + reason=timeout 메트릭이 증가한다`() {
        val fixture = scenarioFixture(seed = 903)
        val baseline = fallbackCount("timeout")

        stubGitHubAndAi(fixture, reviewId = 9913L)
        val inProgressBody = """{"analysis_id":"test-id-903","status":"IN_PROGRESS","issues":[]}"""
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(okJson(inProgressBody)),
        )
        wireMock.stubFor(
            get(urlPathMatching("/agent/analyze/.+"))
                .willReturn(okJson(inProgressBody)),
        )

        postWebhook(fixture)

        await.atMost(15, SECONDS).untilAsserted {
            wireMock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/repos/${fixture.repo}/pulls/${fixture.prNumber}/reviews")),
            )
            assertThat(fallbackCount("timeout")).isEqualTo(baseline + 1.0)
        }
    }

    private fun fallbackCount(reason: String): Double =
        meterRegistry.find("agent.fallback.count").tag("reason", reason).counter()?.count() ?: 0.0

    // 시나리오별 유일성 보장 — DB 잔존물과 WireMock 매칭 충돌 회피
    private data class ScenarioFixture(
        val installationId: Long,
        val repo: String,
        val prNumber: Int,
        val headSha: String,
    )

    private fun scenarioFixture(seed: Int) = ScenarioFixture(
        installationId = 44_000_000L + seed,
        repo = "owner/repo-fallback-$seed",
        prNumber = seed,
        headSha = "sha-${seed}-deadbeef",
    )

    private fun stubGitHubAndAi(fixture: ScenarioFixture, reviewId: Long) {
        WireMockStubs.stubInstallationToken(wireMock, fixture.installationId)
        WireMockStubs.stubPrDiff(wireMock, fixture.repo, fixture.prNumber, AnthropicResponseFixtures.SIMPLE_DIFF)
        stubPrFilesWithSecurity(fixture.repo, fixture.prNumber)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, fixture.repo, fixture.prNumber, reviewId = reviewId)
    }

    private fun stubPrFilesWithSecurity(repo: String, prNumber: Int) {
        val (owner, repoName) = repo.split("/", limit = 2)
        wireMock.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber/files"))
                .willReturn(
                    okJson("""[{"filename":"src/main/kotlin/SecurityConfig.kt","changes":5,"status":"modified","additions":5,"deletions":0}]"""),
                ),
        )
    }

    private fun postWebhook(fixture: ScenarioFixture) {
        val payload = buildPayload(fixture)
        val signature = "sha256=${computeSignature(payload.toByteArray(Charsets.UTF_8), properties.app.webhookSecret)}"
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", signature)
            .header("X-GitHub-Event", "pull_request")
            .body(payload)
            .exchange()
            .expectStatus().isEqualTo(202)
    }

    private fun buildPayload(fixture: ScenarioFixture): String = """
        {
          "action": "opened",
          "installation": { "id": ${fixture.installationId} },
          "repository": { "full_name": "${fixture.repo}" },
          "pull_request": {
            "number": ${fixture.prNumber},
            "head": { "sha": "${fixture.headSha}" },
            "title": "test: agent fallback",
            "user": { "login": "tester" }
          }
        }
    """.trimIndent()
}
