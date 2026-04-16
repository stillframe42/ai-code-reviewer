package stillframe42.aicodereviewer.common.observability

// langfuse.enabled=false 시 등록되는 아무것도 하지 않는 구현체
class NoopObservabilityAdapter : ObservabilityPort {
    override fun startSpan(name: String, input: Map<String, Any>, metadata: Map<String, Any>): SpanHandle =
        SpanHandle(spanId = "", traceId = "")
    override fun endSpan(handle: SpanHandle, output: Map<String, Any>, metadata: Map<String, Any>) = Unit
    override fun endSpanWithError(handle: SpanHandle, error: String) = Unit
}
