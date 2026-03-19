package stillframe42.aicodereviewer.review

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.chat.AiProvider

// ReviewService 통합 테스트 — 실제 AI API를 호출합니다.
// 실제 API 키가 설정된 환경에서만 실행됩니다.
@SpringBootTest
class ReviewServiceTest {

    @Autowired
    private lateinit var reviewService: ReviewService

    @Test
    fun `코드를 리뷰하면 구조화된 결과를 반환한다`() = runBlocking {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        val result = reviewService.reviewCode(
            code = "fun add(a: Int, b: Int) = a + b",
            provider = AiProvider.ANTHROPIC
        )

        // 점수 범위 검증
        assertThat(result.score).isBetween(1, 10)
        // 총평 비어있지 않음 검증
        assertThat(result.summary).isNotBlank()
        // issues, positives는 null이 아닌 리스트여야 함
        assertThat(result.issues).isNotNull
        assertThat(result.positives).isNotNull
    }

    @Test
    fun `문제가 있는 코드를 리뷰하면 이슈를 감지한다`() = runBlocking {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        // 명백히 문제가 있는 코드 (NPE 위험, 예외 처리 없음)
        val result = reviewService.reviewCode(
            code = """
                fun divide(a: Int, b: Int): Int {
                    return a / b
                }
            """.trimIndent(),
            provider = AiProvider.ANTHROPIC
        )

        assertThat(result.score).isBetween(1, 10)
        assertThat(result.summary).isNotBlank()
        assertThat(result.issues).isNotEmpty
    }
}
