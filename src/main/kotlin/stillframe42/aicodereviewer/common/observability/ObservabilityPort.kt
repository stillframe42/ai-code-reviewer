package stillframe42.aicodereviewer.common.observability

// 범용 span 추적 아웃바운드 포트 — Langfuse 구현 또는 no-op 구현으로 교체 가능
// 계약: 구현체는 메인 플로우를 방해하지 않도록 내부에서 예외를 억제해야 한다
interface ObservabilityPort {
    // traceId 를 생성해 현재 스레드의 ThreadLocal 에 설정한다 — 요청 처리의 trace 루트를 연다.
    // 병렬 자식 태스크에는 TraceContextPropagation 이 복사를 담당한다
    fun beginTrace() {}

    // 스레드 재사용 오염 방지 — beginTrace 를 호출한 쪽이 finally 에서 반드시 호출한다
    fun clearTrace() {}

    // withSpan 헬퍼가 부모 spanId 를 set/restore 할 때 사용한다
    fun currentSpanId(): String? = null
    fun activateSpan(spanId: String?) {}

    fun startSpan(name: String, input: Map<String, Any> = emptyMap(), metadata: Map<String, Any> = emptyMap()): SpanHandle
    fun endSpan(handle: SpanHandle, output: Map<String, Any> = emptyMap(), metadata: Map<String, Any> = emptyMap())
    fun endSpanWithError(handle: SpanHandle, error: String)
}
