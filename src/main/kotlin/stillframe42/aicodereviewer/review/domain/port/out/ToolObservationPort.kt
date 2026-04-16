package stillframe42.aicodereviewer.review.domain.port.out

import stillframe42.aicodereviewer.common.observability.ObservabilityPort

// Tool 실행 추적 아웃바운드 포트 — ObservabilityPort 확장
// 기존 tool 전용 메서드는 호환성을 위해 유지하며, 새 코드는 ObservabilityPort 메서드를 사용한다
interface ToolObservationPort : ObservabilityPort {
    fun startSpan(toolName: String, input: Map<String, Any>): String
    fun endSpan(spanId: String, output: String)
    fun endSpanWithError(spanId: String, error: String)
}
