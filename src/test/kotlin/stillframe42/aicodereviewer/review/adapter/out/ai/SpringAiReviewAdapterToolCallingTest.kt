package stillframe42.aicodereviewer.review.adapter.out.ai

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.model.ReviewMode

// Tool Calling 활성화 상태에서 SpringAiReviewAdapter 전체 동작 확인 통합 테스트 — WireMock 모킹
class SpringAiReviewAdapterToolCallingTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var springAiReviewAdapter: SpringAiReviewAdapter

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubInstallationToken(wireMock, WireMockStubs.TEST_INSTALLATION_ID)
        WireMockStubs.stubAnthropicReview(wireMock)
    }

    private val sampleDiff = """
        diff --git a/README.md b/README.md
        index 1234567..89abcde 100644
        --- a/README.md
        +++ b/README.md
        @@ -1,3 +1,4 @@
         # AI Code Reviewer
        +
        +GitHub Pull Request를 자동으로 리뷰하는 Spring AI 기반 서비스입니다.
    """.trimIndent()

    @Test
    fun `WithGitHubTools 모드로 코드 리뷰 요청 시 CodeReview 결과를 반환한다`() = runBlocking {
        // WireMock이 stop_reason=end_turn을 반환하므로 Tool 호출 없이 리뷰가 완료된다
        val result = springAiReviewAdapter.reviewCode(
            code = sampleDiff,
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.WithGitHubTools(installationId = WireMockStubs.TEST_INSTALLATION_ID),
        )

        assertThat(result).isNotNull()
        assertThat(result.summary).isNotBlank()
        assertThat(result.overallScore).isBetween(0, 10)
        Unit
    }

    @Test
    fun `WithGitHubTools 모드로 리뷰 시 toolCallCount가 0 이상으로 설정된다`() = runBlocking {
        val result = springAiReviewAdapter.reviewCode(
            code = sampleDiff,
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.WithGitHubTools(installationId = WireMockStubs.TEST_INSTALLATION_ID),
        )

        assertThat(result.toolCallCount).isGreaterThanOrEqualTo(0)
        Unit
    }

    @Test
    fun `Simple 모드로 리뷰 시 toolCallCount는 0이다`() = runBlocking {
        val result = springAiReviewAdapter.reviewCode(
            code = "fun add(a: Int, b: Int) = a + b",
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.Simple,
        )

        assertThat(result.toolCallCount).isEqualTo(0)
        Unit
    }
}
