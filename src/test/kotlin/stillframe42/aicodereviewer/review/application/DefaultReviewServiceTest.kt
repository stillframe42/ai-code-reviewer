package stillframe42.aicodereviewer.review.application

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

// DefaultReviewService 통합 테스트 — WireMock으로 AI API를 모킹합니다.
class DefaultReviewServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    @BeforeEach
    fun setUpStubs() {
        // 이슈 포함 응답으로 설정 — 이슈 감지 테스트도 커버하고 기본 테스트도 통과
        WireMockStubs.stubAnthropicReviewWithIssues(wireMock)
    }

    @Test
    fun `코드를 리뷰하면 구조화된 결과를 반환한다`() = runBlocking {
        val result = reviewUseCase.reviewCode(
            code = "fun add(a: Int, b: Int) = a + b",
            provider = AiProvider.ANTHROPIC
        )

        // 점수 범위 검증
        assertThat(result.overallScore).isBetween(1, 10)
        // 총평 비어있지 않음 검증
        assertThat(result.summary).isNotBlank()
        // issues, positives는 null이 아닌 리스트여야 함
        assertThat(result.issues).isNotNull
        assertThat(result.positives).isNotNull
        Unit
    }

    @Test
    fun `문제가 있는 코드를 리뷰하면 이슈를 감지한다`() = runBlocking {
        // REVIEW_WITH_ISSUES 픽스처가 이슈 1건을 포함하므로 isNotEmpty 검증 통과
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
        Unit
    }
}
