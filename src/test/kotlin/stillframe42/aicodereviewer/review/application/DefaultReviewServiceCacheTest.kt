package stillframe42.aicodereviewer.review.application

import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ReactiveRedisTemplate
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

// DefaultReviewService 캐시 통합 테스트
// 동일 diff의 두 번째 리뷰 호출이 캐시에서 반환되어 AI 호출이 발생하지 않음을 검증한다.
class DefaultReviewServiceCacheTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    @BeforeEach
    fun setUpCacheTest() {
        WireMockStubs.stubAnthropicReviewWithIssues(wireMock)
    }

    @Test
    fun `동일 diff를 두 번 리뷰하면 두 번째는 캐시에서 반환되어 AI 호출이 발생하지 않는다`(): Unit = runBlocking {
        val diff = """
            diff --git a/src/MyService.kt b/src/MyService.kt
            --- a/src/MyService.kt
            +++ b/src/MyService.kt
            @@ -1,1 +1,2 @@
             class MyService
            +    // 캐시 테스트용 고유 변경
        """.trimIndent()

        val result1 = reviewUseCase.reviewCode(
            code = diff,
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
        )
        val result2 = reviewUseCase.reviewCode(
            code = diff,
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
        )

        // AI는 1회만 호출됨
        wireMock.verify(exactly(1), postRequestedFor(urlPathEqualTo("/v1/messages")))
        // 결과 동일
        assertThat(result2.summary).isEqualTo(result1.summary)
        assertThat(result2.overallScore).isEqualTo(result1.overallScore)
    }

    @Test
    fun `다른 diff는 캐시를 공유하지 않아 각각 AI를 호출한다`(): Unit = runBlocking {
        val diff1 = """
            diff --git a/src/FooService.kt b/src/FooService.kt
            +++ b/src/FooService.kt
            +class FooService
        """.trimIndent()
        val diff2 = """
            diff --git a/src/BarService.kt b/src/BarService.kt
            +++ b/src/BarService.kt
            +class BarService
        """.trimIndent()

        reviewUseCase.reviewCode(code = diff1, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())
        reviewUseCase.reviewCode(code = diff2, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())

        // 서로 다른 diff이므로 각각 AI 호출 → 총 2회
        wireMock.verify(exactly(2), postRequestedFor(urlPathEqualTo("/v1/messages")))
    }
}
