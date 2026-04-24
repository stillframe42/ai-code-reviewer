package stillframe42.aicodereviewer.integration

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import stillframe42.aicodereviewer.config.GitHubProperties
import stillframe42.aicodereviewer.github.adapter.`in`.web.computeSignature
import stillframe42.aicodereviewer.github.adapter.out.persistence.ProcessedPullRequestEventEntity
import stillframe42.aicodereviewer.github.adapter.out.persistence.ProcessedPullRequestEventRepository
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewIssueCategoryRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewRequestRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ReviewResultRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.ToolCallLogRepository
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

// Webhook 수신 → AI 리뷰 생성 → GitHub PR 리뷰 등록 전체 플로우 통합 테스트
class WebhookFlowIntegrationTest : AbstractIntegrationTest() {

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

    // 테스트에서 사용하는 payload — owner/repo PR #42, headSha: abc123def456
    private val pullRequestPayload = """
        {
          "action": "opened",
          "installation": { "id": 12345678 },
          "repository": { "full_name": "owner/repo" },
          "pull_request": {
            "number": 42,
            "head": { "sha": "abc123def456" },
            "title": "feat: 새로운 기능",
            "user": { "login": "octocat" }
          }
        }
    """.trimIndent()

    // 설정에서 주입받은 secret으로 서명 계산 — 설정값 변경에도 테스트가 깨지지 않도록 한다
    private fun sign(payload: String): String =
        "sha256=${computeSignature(payload.toByteArray(Charsets.UTF_8), properties.app.webhookSecret)}"

    @AfterEach
    fun cleanDb() {
        // FK 순서: reviewIssueCategoryRepository → toolCallLogRepository → reviewResultRepository → reviewRequestRepository → processedEventRepository
        reviewIssueCategoryRepository.deleteAll()
        toolCallLogRepository.deleteAll()
        reviewResultRepository.deleteAll()
        reviewRequestRepository.deleteAll()
        processedEventRepository.deleteAll()
    }

    @Test
    fun `PR webhook 수신 시 전체 플로우가 완료되고 GitHub에 리뷰가 등록된다`() {
        // 모든 외부 API stub 등록
        WireMockStubs.stubInstallationToken(wireMock, 12345678L)
        WireMockStubs.stubPrDiff(wireMock, "owner/repo", 42, AnthropicResponseFixtures.SIMPLE_DIFF)
        WireMockStubs.stubPrFiles(wireMock, "owner/repo", 42)
        WireMockStubs.stubAnthropicReview(wireMock)
        WireMockStubs.stubPostPrReview(wireMock, "owner/repo", 42)

        // Webhook POST — fire-and-forget이므로 202 즉시 반환
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(pullRequestPayload))
            .header("X-GitHub-Event", "pull_request")
            .body(pullRequestPayload)
            .exchange()
            .expectStatus().isEqualTo(202)

        // 백그라운드 처리가 완료될 때까지 최대 10초 대기 — WireMock에 리뷰 등록 요청 1회 수신 확인
        await.atMost(10, SECONDS).untilAsserted {
            wireMock.verify(1, postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/reviews")))
        }
    }

    @Test
    fun `이미 처리된 이벤트는 중복 처리하지 않는다`() {
        // 동일 (레포, PR번호, SHA) 조합을 DB에 미리 저장 — 중복으로 인식되어야 한다
        processedEventRepository.save(
            ProcessedPullRequestEventEntity(
                repositoryFullName = "owner/repo",
                pullRequestNumber = 42,
                headSha = "abc123def456",
                reviewId = 9001L,
            )
        )

        // Webhook POST — 서명은 올바르므로 202 반환
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(pullRequestPayload))
            .header("X-GitHub-Event", "pull_request")
            .body(pullRequestPayload)
            .exchange()
            .expectStatus().isEqualTo(202)

        // 중복 체크 후 즉시 종료 — 충분한 대기 후에도 GitHub 리뷰 등록 요청이 없어야 한다
        await.during(500, MILLISECONDS).atMost(1, SECONDS).untilAsserted {
            wireMock.verify(0, postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/reviews")))
        }
    }

    @Test
    fun `AI API 오류 시 에러 안내 리뷰가 GitHub에 등록된다`() {
        // PR #99 사용 — 다른 테스트(PR #42)에서 누출된 코루틴이 이 테스트의 stub을 오염시키지 않도록
        // applicationScope는 싱글톤이므로 이전 테스트의 백그라운드 코루틴이 다음 테스트와 겹칠 수 있다.
        // PR 번호를 분리하면 누출된 코루틴이 PR 42용 stub에 접근해도 이 테스트의 검증에 영향 없음.
        val errorTestPayload = """
            {
              "action": "opened",
              "installation": { "id": 12345678 },
              "repository": { "full_name": "owner/repo" },
              "pull_request": {
                "number": 99,
                "head": { "sha": "error999def456" },
                "title": "feat: 오류 시나리오 테스트",
                "user": { "login": "octocat" }
              }
            }
        """.trimIndent()

        // Given: GitHub API는 정상, AI API는 오류 반환
        WireMockStubs.stubInstallationToken(wireMock, 12345678L)
        WireMockStubs.stubPrDiff(wireMock, "owner/repo", 99, AnthropicResponseFixtures.SIMPLE_DIFF)
        WireMockStubs.stubPrFiles(wireMock, "owner/repo", 99)
        WireMockStubs.stubAnthropicError(wireMock)            // AI 500 오류
        WireMockStubs.stubPostPrReview(wireMock, "owner/repo", 99, reviewId = 9002L)

        // When: Webhook 전송
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", sign(errorTestPayload))
            .header("X-GitHub-Event", "pull_request")
            .body(errorTestPayload)
            .exchange()
            .expectStatus().isEqualTo(202)

        // Then: AI 재시도(2회 × 5s) 포함 최대 20초 대기
        // 에러 안내 본문으로 리뷰가 등록되어야 한다
        await.atMost(20, SECONDS).untilAsserted {
            wireMock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/99/reviews"))
                    .withRequestBody(containing("오류가 발생했습니다"))
            )
        }
    }

    @Test
    fun `잘못된 서명이면 401 Unauthorized 반환하고 리뷰를 등록하지 않는다`() {
        // 잘못된 서명 헤더로 Webhook POST
        client.post().uri("/api/github/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Hub-Signature-256", "sha256=invalidsignature")
            .header("X-GitHub-Event", "pull_request")
            .body(pullRequestPayload)
            .exchange()
            .expectStatus().isUnauthorized

        // 서명 검증 실패로 처리 자체가 시작되지 않으므로 GitHub 리뷰 등록 요청이 없어야 한다
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/reviews")))
    }
}
