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
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// SpringAiReviewAdapter Tool Calling 통합 테스트 — WireMock으로 AI API를 모킹합니다.
class SpringAiReviewAdapterToolTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var aiReviewPort: AiReviewPort

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubInstallationToken(wireMock, WireMockStubs.TEST_INSTALLATION_ID)
        WireMockStubs.stubAnthropicReview(wireMock)
    }

    @Test
    fun `installationId 없이 호출하면 Tool 없이 정상 리뷰를 반환한다`() = runBlocking {
        val result = aiReviewPort.reviewCode(
            code = "fun add(a: Int, b: Int) = a + b",
            provider = AiProvider.ANTHROPIC,
        )

        assertThat(result.overallScore).isBetween(0, 10)
        assertThat(result.summary).isNotBlank()
        Unit
    }

    @Test
    fun `installationId 제공 시 Tool이 등록된 상태로 리뷰가 완료된다`() = runBlocking {
        // WireMock이 stop_reason=end_turn을 반환하므로 Tool 호출 없이 리뷰가 완료된다
        val result = aiReviewPort.reviewCode(
            code = """
                diff --git a/src/main/kotlin/com/example/Foo.kt b/src/main/kotlin/com/example/Foo.kt
                --- a/src/main/kotlin/com/example/Foo.kt
                +++ b/src/main/kotlin/com/example/Foo.kt
                @@ -1,3 +1,5 @@
                 package com.example
                +
                +import java.util.UUID
                +
                 fun generateId() = UUID.randomUUID().toString()
            """.trimIndent(),
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.WithGitHubTools(WireMockStubs.TEST_INSTALLATION_ID),
        )

        assertThat(result.overallScore).isBetween(0, 10)
        assertThat(result.summary).isNotBlank()
        Unit
    }
}
