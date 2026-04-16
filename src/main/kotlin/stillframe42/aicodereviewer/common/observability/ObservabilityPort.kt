package stillframe42.aicodereviewer.common.observability

// 범용 span 추적 아웃바운드 포트 — Langfuse 구현 또는 no-op 구현으로 교체 가능
// 계약: 구현체는 메인 플로우를 방해하지 않도록 내부에서 예외를 억제해야 한다
interface ObservabilityPort {
    fun startSpan(name: String, input: Map<String, Any> = emptyMap(), metadata: Map<String, Any> = emptyMap()): SpanHandle
    fun endSpan(handle: SpanHandle, output: Map<String, Any> = emptyMap(), metadata: Map<String, Any> = emptyMap())
    fun endSpanWithError(handle: SpanHandle, error: String)
}
