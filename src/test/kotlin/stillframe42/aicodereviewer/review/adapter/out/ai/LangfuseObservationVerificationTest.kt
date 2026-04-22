package stillframe42.aicodereviewer.review.adapter.out.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.model.ReviewContext
import stillframe42.aicodereviewer.review.domain.model.ReviewMode

// Langfuse 추적 항목 통합 검증 — WireMock 으로 페이로드 캡처해 검증.
// langfuse.enabled=true 오버라이드로 LangfuseObservationHandler + LangfuseToolSpanAdapter 활성화.
@TestPropertySource(properties = ["langfuse.enabled=true"])
class LangfuseObservationVerificationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var springAiReviewAdapter: SpringAiReviewAdapter

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubLangfuseIngestion(wireMock)
        WireMockStubs.stubInstallationToken(wireMock, WireMockStubs.TEST_INSTALLATION_ID)
    }

    // WireMock이 캡처한 Langfuse ingestion 요청의 batch 이벤트를 모두 추출
    private fun collectLangfuseEvents(): List<Map<String, Any>> {
        val requests = WireMockStubs.findLangfuseIngestionRequests(wireMock)
        return requests.flatMap { req ->
            val body: Map<String, Any> = objectMapper.readValue(req.bodyAsString)
            @Suppress("UNCHECKED_CAST")
            (body["batch"] as? List<Map<String, Any>>) ?: emptyList()
        }
    }

    @Test
    fun `Simple 리뷰 시 Langfuse에 trace-create, generation-create, generation-update가 전송된다`() = runBlocking {
        WireMockStubs.stubAnthropicReview(wireMock)
        val reviewContext = ReviewContext(
            reviewRequestId = 1L,
            prNumber = WireMockStubs.TEST_PR_NUMBER,
            repoFullName = WireMockStubs.TEST_REPO,
        )

        springAiReviewAdapter.reviewCode(
            code = "fun hello() = println(\"hello\")",
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.Simple,
            reviewContext = reviewContext,
        )

        val allEvents = collectLangfuseEvents()
        val eventTypes = allEvents.map { it["type"] as String }

        // 프롬프트/응답 기록 검증
        assertThat(eventTypes).contains("trace-create", "generation-create", "generation-update")

        // 토큰 사용량 및 응답시간 기록 검증
        val generationUpdate = allEvents.first { it["type"] == "generation-update" }
        @Suppress("UNCHECKED_CAST")
        val updateBody = generationUpdate["body"] as Map<String, Any>
        assertThat(updateBody).containsKey("usage")
        assertThat(updateBody).containsKey("endTime")

        Unit
    }

    @Test
    fun `Tool Calling 리뷰 시 Langfuse에 span-create와 span-update가 전송된다`() = runBlocking {
        WireMockStubs.stubAnthropicWithToolCall(wireMock)
        WireMockStubs.stubGitHubPrDescription(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER)

        val reviewContext = ReviewContext(
            reviewRequestId = 2L,
            prNumber = WireMockStubs.TEST_PR_NUMBER,
            repoFullName = WireMockStubs.TEST_REPO,
        )

        springAiReviewAdapter.reviewCode(
            code = "fun hello() = println(\"hello\")",
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.WithGitHubTools(installationId = WireMockStubs.TEST_INSTALLATION_ID),
            reviewContext = reviewContext,
        )

        val allEvents = collectLangfuseEvents()
        val eventTypes = allEvents.map { it["type"] as String }

        // Tool 호출 이력이 Span으로 기록되는지 확인
        assertThat(eventTypes).contains("span-create", "span-update")

        // Parent Trace → Child Span 구조 검증
        val traceCreate = allEvents.first { it["type"] == "trace-create" }
        @Suppress("UNCHECKED_CAST")
        val traceId = (traceCreate["body"] as Map<String, Any>)["id"] as String

        val spanCreate = allEvents.first { it["type"] == "span-create" }
        @Suppress("UNCHECKED_CAST")
        val spanBody = spanCreate["body"] as Map<String, Any>
        assertThat(spanBody["traceId"]).isEqualTo(traceId)

        // span에 Tool 이름이 포함되는지 확인
        assertThat(spanBody["name"]).isEqualTo("getPRDescription")

        Unit
    }

    @Test
    fun `ReviewContext 메타데이터가 Trace에 포함된다`() = runBlocking {
        WireMockStubs.stubAnthropicReview(wireMock)
        val reviewContext = ReviewContext(
            reviewRequestId = 99L,
            prNumber = 7,
            repoFullName = "test-owner/test-repo",
        )

        springAiReviewAdapter.reviewCode(
            code = "fun hello() = println(\"hello\")",
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.Simple,
            reviewContext = reviewContext,
        )

        val allEvents = collectLangfuseEvents()

        val traceCreate = allEvents.first { it["type"] == "trace-create" }
        @Suppress("UNCHECKED_CAST")
        val traceBody = traceCreate["body"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val metadata = traceBody["metadata"] as Map<String, Any>

        assertThat(metadata["review.request.id"]).isEqualTo("99")
        assertThat(metadata["review.pr.number"]).isEqualTo("7")
        assertThat(metadata["review.repo"]).isEqualTo("test-owner/test-repo")

        Unit
    }
}
