package stillframe42.aicodereviewer.common.metrics

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import stillframe42.aicodereviewer.config.GitHubProperties
import stillframe42.aicodereviewer.github.adapter.`in`.web.computeSignature
import stillframe42.aicodereviewer.github.adapter.out.persistence.ProcessedPullRequestEventRepository
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.adapter.out.persistence.LlmCostLogRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ToolCallLogRepository

// 리뷰 실행 후 MeterRegistry에 메트릭이 기록되는지 검증하는 통합 테스트
// PR #200, sha="metrics001sha" — 다른 테스트(PR #42)와 충돌 방지
class MetricsIntegrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var meterRegistry: MeterRegistry
    @Autowired private lateinit var properties: GitHubProperties
    @Autowired private lateinit var processedEventRepository: ProcessedPullRequestEventRepository
    @Autowired private lateinit var reviewIssueCategoryRepository: ReviewIssueCategoryRepository
    @Autowired private lateinit var reviewResultRepository: ReviewResultRepository
    @Autowired private lateinit var reviewRequestRepository: ReviewRequestRepository
    @Autowired private lateinit var toolCallLogRepository: ToolCallLogRepository
    @Autowired private lateinit var llmCostLogRepository: LlmCostLogRepository

    // REVIEW_WITH_ISSUES 픽스처: 이슈 1건 (severity=MAJOR → HIGH), usage.input_tokens=100, output_tokens=80
    private val payload = """
        {
          "action": "opened",
          "installation": { "id": 12345678 },
          "repository": { "full_name": "owner/repo" },
          "pull_request": {
            "number": 200,
            "head": { "sha": "metrics001sha" },
            "title": "feat: 메트릭 테스트 PR",
            "user": { "login": "octocat" }
          }
        }
    """.trimIndent()

    private fun sign(body: String) =
        "sha256=${computeSignature(body.toByteArray(Charsets.UTF_8), properties.app.webhookSecret)}"

    @BeforeEach
    fun stubExternalApis() {
        WireMockStubs.stubAnyInstallationToken(wireMock)
        WireMockStubs.stubPrDiff(wireMock, "owner/repo", 200, AnthropicResponseFixtures.SIMPLE_DIFF)
        WireMockStubs.stubPrFiles(wireMock, "owner/repo", 200)
        WireMockStubs.stubAnthropicReviewWithIssues(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, "owner/repo", 200, reviewId = 9200L)
        // RAG ConventionContextService가 리뷰 플로우에서 임베딩 API를 호출하므로 스텁 등록
        WireMockStubs.stubOpenAiEmbedding(wireMock)
    }

    @AfterEach
    fun cleanDb() {
        reviewIssueCategoryRepository.deleteAll()
        toolCallLogRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
        processedEventRepository.deleteAll()
        llmCostLogRepository.deleteAll()
    }

    private fun sendWebhook() {
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(payload))
            .header("X-GitHub-Event", "pull_request")
            .body(payload)
            .exchange()
            .expectStatus().isEqualTo(202)
    }

    private fun waitForReviewPosted() {
        await.atMost(10, SECONDS).untilAsserted {
            wireMock.verify(1, postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/200/reviews")))
        }
    }

    // 리뷰 플로우의 마지막 단계(markAsProcessed)가 완료될 때까지 대기한다.
    // 다음 테스트 시작 전에 이 메서드가 반환되어야 processedEvent 중복 키 오류를 방지할 수 있다.
    private fun waitForProcessed() {
        await.atMost(10, SECONDS).until { processedEventRepository.count() > 0 }
    }

    // before 값을 읽어 delta로 검증 — 다른 테스트와 meterRegistry 공유하므로 누적값이 아닌 증가분을 검증한다
    // Search.tags(Iterable<Tag>) 오버로드 사용 — spread 연산자(*) 없이 리스트를 직접 전달한다
    private fun tagsFrom(vararg pairs: String) =
        pairs.toList().chunked(2).map { Tag.of(it[0], it[1]) }

    private fun counterBefore(name: String, vararg tags: String): Double =
        meterRegistry.find(name).tags(tagsFrom(*tags)).counter()?.count() ?: 0.0

    private fun summaryCountBefore(name: String, vararg tags: String): Long =
        meterRegistry.find(name).tags(tagsFrom(*tags)).summary()?.count() ?: 0L

    private fun timerCountBefore(name: String, vararg tags: String): Long =
        meterRegistry.find(name).tags(tagsFrom(*tags)).timer()?.count() ?: 0L

    @Test
    fun `리뷰 성공 시 review_requests_total DONE 카운터가 증가한다`() {
        val before = counterBefore("review.requests.total", "repo", "owner/repo", "status", "DONE")

        sendWebhook()
        waitForReviewPosted()
        // 리뷰 플로우 전체 완료(markAsProcessed) 대기 — 다음 테스트에서 중복 키 방지
        waitForProcessed()

        val after = meterRegistry.get("review.requests.total")
            .tag("repo", "owner/repo").tag("status", "DONE").counter().count()
        assertThat(after - before).isEqualTo(1.0)
    }

    @Test
    fun `리뷰 성공 시 review_duration 타이머가 기록된다`() {
        val before = timerCountBefore("review.duration", "repo", "owner/repo")

        sendWebhook()
        waitForReviewPosted()
        waitForProcessed()

        val after = meterRegistry.get("review.duration").tag("repo", "owner/repo").timer().count()
        assertThat(after - before).isEqualTo(1L)
    }

    @Test
    fun `MAJOR 이슈는 review_issues_found HIGH로 기록된다`() {
        // REVIEW_WITH_ISSUES: severity=MAJOR → HIGH 매핑
        val before = summaryCountBefore("review.issues.found", "severity", "HIGH")

        sendWebhook()
        waitForReviewPosted()
        waitForProcessed()

        val after = meterRegistry.get("review.issues.found").tag("severity", "HIGH").summary().count()
        assertThat(after - before).isEqualTo(1L)
    }

    @Test
    fun `LLM 토큰 메트릭이 기록된다`() {
        // REVIEW_WITH_ISSUES: input_tokens=100, output_tokens=80
        val promptBefore = counterBefore("llm.tokens.used", "model", "claude-haiku-4-5-20251001", "type", "prompt")
        val completionBefore = counterBefore("llm.tokens.used", "model", "claude-haiku-4-5-20251001", "type", "completion")

        sendWebhook()
        // PR 리뷰 등록 완료 대기 (postPrReview 성공 확인) — 이후 CostTrackingAdvisor 저장을 기다린다
        waitForReviewPosted()
        // CostTrackingAdvisor는 백그라운드 가상 스레드에서 LlmCostLog를 저장 — DB 저장 완료를 기다린 후 메트릭을 검증한다
        await.atMost(10, SECONDS).until { llmCostLogRepository.count() > 0 }

        val promptAfter = meterRegistry.get("llm.tokens.used")
            .tag("model", "claude-haiku-4-5-20251001").tag("type", "prompt").counter().count()
        assertThat(promptAfter - promptBefore).isEqualTo(100.0)

        val completionAfter = meterRegistry.get("llm.tokens.used")
            .tag("model", "claude-haiku-4-5-20251001").tag("type", "completion").counter().count()
        assertThat(completionAfter - completionBefore).isEqualTo(80.0)

        waitForProcessed()
    }

    @Test
    fun `LLM 비용 메트릭이 마이크로달러로 기록된다`() {
        val before = counterBefore("llm.cost.total", "model", "claude-haiku-4-5-20251001")

        sendWebhook()
        // PR 리뷰 등록 완료 대기 (postPrReview 성공 확인) — 이후 CostTrackingAdvisor 저장을 기다린다
        waitForReviewPosted()
        // CostTrackingAdvisor는 백그라운드 가상 스레드에서 LlmCostLog를 저장 — DB 저장 완료를 기다린 후 메트릭을 검증한다
        await.atMost(10, SECONDS).until { llmCostLogRepository.count() > 0 }

        val after = meterRegistry.get("llm.cost.total")
            .tag("model", "claude-haiku-4-5-20251001").counter().count()
        // input=100 × 0.000800/1k + output=80 × 0.004000/1k = 0.000080 + 0.000320 = 0.000400 USD = 400 마이크로달러
        assertThat(after - before).isEqualTo(400.0)

        waitForProcessed()
    }
}
