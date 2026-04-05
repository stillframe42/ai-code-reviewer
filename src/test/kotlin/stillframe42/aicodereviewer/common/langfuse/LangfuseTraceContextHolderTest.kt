package stillframe42.aicodereviewer.common.langfuse

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

// LangfuseTraceContextHolder ThreadLocal 동작 단위 테스트
class LangfuseTraceContextHolderTest {

    @AfterEach
    fun cleanup() {
        LangfuseTraceContextHolder.clear()
    }

    @Test
    fun `set 후 get은 설정한 traceId를 반환한다`() {
        LangfuseTraceContextHolder.set("test-trace-id")
        assertThat(LangfuseTraceContextHolder.get()).isEqualTo("test-trace-id")
    }

    @Test
    fun `초기 상태에서 get은 null을 반환한다`() {
        assertThat(LangfuseTraceContextHolder.get()).isNull()
    }

    @Test
    fun `clear 후 get은 null을 반환한다`() {
        LangfuseTraceContextHolder.set("test-trace-id")
        LangfuseTraceContextHolder.clear()
        assertThat(LangfuseTraceContextHolder.get()).isNull()
    }
}
