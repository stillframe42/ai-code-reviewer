package stillframe42.aicodereviewer.common.langfuse

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TraceContextPropagationTest {

    @AfterEach
    fun tearDown() {
        LangfuseTraceContextHolder.clear()
        LangfuseSpanContextHolder.clear()
        ObservationSessionContextHolder.local.remove()
    }

    @Test
    fun `캡처한 traceId 가 자식 스레드에서 보인다`() {
        LangfuseTraceContextHolder.set("trace-1")
        var seen: String? = null
        val task = TraceContextPropagation.capture { seen = LangfuseTraceContextHolder.get() }
        Thread.ofVirtual().start(task.toRunnable()).join()
        assertThat(seen).isEqualTo("trace-1")
    }

    @Test
    fun `자식 스레드 실행 종료 후 자식의 ThreadLocal 이 정리된다`() {
        LangfuseTraceContextHolder.set("trace-1")
        var afterClear: String? = "sentinel"
        val task = TraceContextPropagation.capture { }
        Thread.ofVirtual().start {
            task()
            afterClear = LangfuseTraceContextHolder.get()
        }.join()
        assertThat(afterClear).isNull()
    }

    @Test
    fun `세션 컨텍스트와 spanId 도 함께 전파된다`() {
        LangfuseSpanContextHolder.set("span-1")
        ObservationSessionContextHolder.local.set(ObservationSessionContext("session-1", emptyMap()))
        var seenSpan: String? = null
        var seenSession: String? = null
        val task = TraceContextPropagation.capture {
            seenSpan = LangfuseSpanContextHolder.get()
            seenSession = ObservationSessionContextHolder.local.get()?.sessionId
        }
        Thread.ofVirtual().start(task.toRunnable()).join()
        assertThat(seenSpan).isEqualTo("span-1")
        assertThat(seenSession).isEqualTo("session-1")
    }
}

private fun (() -> Unit).toRunnable() = Runnable { this() }
