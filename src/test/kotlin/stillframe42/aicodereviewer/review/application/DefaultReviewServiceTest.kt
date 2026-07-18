package stillframe42.aicodereviewer.review.application

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

// DefaultReviewService 통합 테스트 — WireMock으로 AI API를 모킹합니다.
class DefaultReviewServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    @BeforeEach
    fun setUpStubs() {
        // 이슈 포함 응답으로 설정 — 이슈 감지 테스트도 커버하고 기본 테스트도 통과
        WireMockStubs.stubAnthropicReviewWithIssues(wireMock)
        // RAG ConventionContextService가 diffOptions 존재 시 임베딩 API를 호출하므로 스텁 등록
        WireMockStubs.stubOpenAiEmbedding(wireMock)
    }

    @Test
    fun `코드를 리뷰하면 구조화된 결과를 반환한다`() {
        val result = reviewUseCase.reviewCode(
            code = "fun add(a: Int, b: Int) = a + b",
            provider = AiProvider.ANTHROPIC
        )

        assertThat(result.overallScore).isBetween(1, 10)
        assertThat(result.summary).isNotBlank()
        assertThat(result.issues).isNotNull
        assertThat(result.positives).isNotNull
    }

    @Test
    fun `문제가 있는 코드를 리뷰하면 이슈를 감지한다`() {
        val result = reviewUseCase.reviewCode(
            code = """
                fun divide(a: Int, b: Int): Int {
                    return a / b
                }
            """.trimIndent(),
            provider = AiProvider.ANTHROPIC
        )

        assertThat(result.overallScore).isBetween(1, 10)
        assertThat(result.summary).isNotBlank()
        assertThat(result.issues).isNotEmpty
    }

    @Test
    fun `CRITICAL 패턴 파일이 포함된 diff는 sonnet 모델을 사용한다`() {
        // SecurityConfig.kt → **/*Security* 패턴 매칭 → CRITICAL → test-sonnet-model
        reviewUseCase.reviewCode(
            code = """
                diff --git a/src/SecurityConfig.kt b/src/SecurityConfig.kt
                --- a/src/SecurityConfig.kt
                +++ b/src/SecurityConfig.kt
                @@ -1,1 +1,2 @@
                 class SecurityConfig
                +    // 보안 강화
            """.trimIndent(),
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("\"model\":\"test-sonnet-model\""))
        )
    }

    @Test
    fun `일반 파일만 포함된 diff는 haiku 모델을 사용한다`() {
        // MyService.kt → 패턴 미매칭 → NORMAL → test-haiku-model
        reviewUseCase.reviewCode(
            code = """
                diff --git a/src/MyService.kt b/src/MyService.kt
                --- a/src/MyService.kt
                +++ b/src/MyService.kt
                @@ -1,1 +1,2 @@
                 class MyService
                +    // 기능 추가
            """.trimIndent(),
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("\"model\":\"test-haiku-model\""))
        )
    }
}
