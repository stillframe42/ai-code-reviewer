package stillframe42.aicodereviewer.common.concurrent

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder

class BlockingTimeoutTest {

    @Test
    fun `제한 시간 내 완료되면 결과를 반환한다`() {
        val result = BlockingTimeout.run(1.seconds) { "ok" }
        assertThat(result).isEqualTo("ok")
    }

    @Test
    fun `제한 시간 초과 시 BlockingTimeoutException 을 던진다`() {
        assertThatThrownBy {
            BlockingTimeout.run(50.milliseconds) { Thread.sleep(5_000) }
        }.isInstanceOf(BlockingTimeoutException::class.java)
    }

    @Test
    fun `블록이 던진 예외는 원본 타입 그대로 전파된다`() {
        assertThatThrownBy {
            BlockingTimeout.run(1.seconds) { throw IllegalStateException("boom") }
        }.isInstanceOf(IllegalStateException::class.java).hasMessage("boom")
    }

    @Test
    fun `부모 스레드의 traceId 가 블록 안에서 보인다`() {
        LangfuseTraceContextHolder.set("trace-bt")
        try {
            val seen = BlockingTimeout.run(1.seconds) { LangfuseTraceContextHolder.get() }
            assertThat(seen).isEqualTo("trace-bt")
        } finally {
            LangfuseTraceContextHolder.clear()
        }
    }
}
