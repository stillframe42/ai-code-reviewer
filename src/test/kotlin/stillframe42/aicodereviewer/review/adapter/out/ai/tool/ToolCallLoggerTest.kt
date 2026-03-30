package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolCallLoggerTest {

    private val toolCallLogger = ToolCallLogger()

    @Test
    fun `block의 반환값을 그대로 반환한다`() {
        val result = toolCallLogger.log("testTool", "arg=value") { "hello" }
        assertThat(result).isEqualTo("hello")
    }

    @Test
    fun `예외가 발생하면 그대로 전파된다`() {
        val exception = RuntimeException("오류 발생")
        val thrown = runCatching {
            toolCallLogger.log("testTool", "arg=value") { throw exception }
        }.exceptionOrNull()
        assertThat(thrown).isSameAs(exception)
    }
}
