package stillframe42.aicodereviewer.common.observability

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WithSpanTest {

    @Test
    fun `정상 완료 시 endSpan이 호출되고 결과가 반환된다`() = runTest {
        val recorder = RecordingObservabilityAdapter()

        val result = recorder.withSpan("test.span", mapOf("key" to "value")) { "hello" }

        assertThat(result).isEqualTo("hello")
        assertThat(recorder.started).hasSize(1)
        assertThat(recorder.started[0].first).isEqualTo("test.span")
        assertThat(recorder.ended).hasSize(1)
        assertThat(recorder.errors).isEmpty()
    }

    @Test
    fun `예외 발생 시 endSpanWithError가 호출되고 예외가 재전파된다`() = runTest {
        val recorder = RecordingObservabilityAdapter()

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                recorder.withSpan<Nothing>("fail.span") {
                    throw IllegalStateException("test error")
                }
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(recorder.started).hasSize(1)
        assertThat(recorder.errors).hasSize(1)
        assertThat(recorder.errors[0].second).isEqualTo("test error")
        assertThat(recorder.ended).isEmpty()
    }

    @Test
    fun `outputMapper로 결과를 output에 매핑한다`() = runTest {
        val recorder = RecordingObservabilityAdapter()

        recorder.withSpan(
            name = "mapped.span",
            outputMapper = { count: Int -> mapOf("count" to count) },
        ) { 42 }

        assertThat(recorder.ended).hasSize(1)
        assertThat(recorder.ended[0].second).containsEntry("count", 42)
    }
}

class RecordingObservabilityAdapter : ObservabilityPort {
    val started = mutableListOf<Pair<String, Map<String, Any>>>()
    val ended = mutableListOf<Pair<SpanHandle, Map<String, Any>>>()
    val errors = mutableListOf<Pair<SpanHandle, String>>()

    override fun startSpan(name: String, input: Map<String, Any>, metadata: Map<String, Any>): SpanHandle {
        started.add(name to input)
        return SpanHandle(spanId = "test-span-${started.size}", traceId = "test-trace")
    }

    override fun endSpan(handle: SpanHandle, output: Map<String, Any>, metadata: Map<String, Any>) {
        ended.add(handle to output)
    }

    override fun endSpanWithError(handle: SpanHandle, error: String) {
        errors.add(handle to error)
    }
}
