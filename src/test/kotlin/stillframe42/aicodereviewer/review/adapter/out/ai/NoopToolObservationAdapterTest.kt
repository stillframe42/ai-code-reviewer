package stillframe42.aicodereviewer.review.adapter.out.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// NoopToolObservationAdapter 단위 테스트
class NoopToolObservationAdapterTest {

    private val adapter = NoopToolObservationAdapter()

    @Test
    fun `startSpan은 빈 문자열을 반환한다`() {
        val spanId = adapter.startSpan("toolName", mapOf("key" to "value"))
        assertThat(spanId).isEmpty()
    }

    @Test
    fun `endSpan은 예외 없이 완료된다`() {
        adapter.endSpan("span-id", "output")
    }

    @Test
    fun `endSpanWithError는 예외 없이 완료된다`() {
        adapter.endSpanWithError("span-id", "error message")
    }
}
