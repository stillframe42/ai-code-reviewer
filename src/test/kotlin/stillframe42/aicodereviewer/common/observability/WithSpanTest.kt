package stillframe42.aicodereviewer.common.observability

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.asContextElement
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

    @Test
    fun `중첩된 withSpan 안에서 시작된 자식 span 은 부모 spanId 를 활성 부모로 인식한다`() = runTest {
        val recorder = RecordingObservabilityAdapter()

        recorder.withSpan("parent.span") {
            recorder.withSpan("child.span") { "result" }
        }

        assertThat(recorder.started.map { it.first }).containsExactly("parent.span", "child.span")
        assertThat(recorder.parentSpanIdsAtStart[0]).isNull()
        assertThat(recorder.parentSpanIdsAtStart[1]).isEqualTo("test-span-1")
    }
}

class RecordingObservabilityAdapter : ObservabilityPort {
    val started = mutableListOf<Pair<String, Map<String, Any>>>()
    val ended = mutableListOf<Pair<SpanHandle, Map<String, Any>>>()
    val errors = mutableListOf<Pair<SpanHandle, String>>()
    val parentSpanIdsAtStart = mutableListOf<String?>()

    private val activeSpanId: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    override fun spanContext(spanId: String): CoroutineContext =
        if (spanId.isEmpty()) EmptyCoroutineContext
        else activeSpanId.asContextElement(spanId)

    override fun startSpan(name: String, input: Map<String, Any>, metadata: Map<String, Any>): SpanHandle {
        started.add(name to input)
        parentSpanIdsAtStart.add(activeSpanId.get())
        return SpanHandle(spanId = "test-span-${started.size}", traceId = "test-trace")
    }

    override fun endSpan(handle: SpanHandle, output: Map<String, Any>, metadata: Map<String, Any>) {
        ended.add(handle to output)
    }

    override fun endSpanWithError(handle: SpanHandle, error: String) {
        errors.add(handle to error)
    }
}
