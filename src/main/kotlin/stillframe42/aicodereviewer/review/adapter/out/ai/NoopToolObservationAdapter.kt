package stillframe42.aicodereviewer.review.adapter.out.ai

import stillframe42.aicodereviewer.review.domain.port.out.ToolObservationPort

// langfuse.enabled=false 시 등록되는 아무것도 하지 않는 구현체
class NoopToolObservationAdapter : ToolObservationPort {
    override fun startSpan(toolName: String, input: Map<String, Any>): String = ""
    override fun endSpan(spanId: String, output: String) = Unit
    override fun endSpanWithError(spanId: String, error: String) = Unit
}
