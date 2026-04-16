package stillframe42.aicodereviewer.common.observability

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// 범용 span 추적 아웃바운드 포트 — Langfuse 구현 또는 no-op 구현으로 교체 가능
// 계약: 구현체는 메인 플로우를 방해하지 않도록 내부에서 예외를 억제해야 한다
interface ObservabilityPort {
    // traceId 를 생성하고 코루틴 컨텍스트 요소로 반환한다
    // withContext(traceContext()) 로 감싸면 하위 모든 코루틴에 traceId 가 전파된다
    fun traceContext(): CoroutineContext = EmptyCoroutineContext

    fun startSpan(name: String, input: Map<String, Any> = emptyMap(), metadata: Map<String, Any> = emptyMap()): SpanHandle
    fun endSpan(handle: SpanHandle, output: Map<String, Any> = emptyMap(), metadata: Map<String, Any> = emptyMap())
    fun endSpanWithError(handle: SpanHandle, error: String)
}
