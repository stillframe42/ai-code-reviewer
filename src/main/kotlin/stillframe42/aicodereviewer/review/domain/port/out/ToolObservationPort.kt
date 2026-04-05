package stillframe42.aicodereviewer.review.domain.port.out

// Tool 실행 추적 아웃바운드 포트 — Langfuse Span 기록 또는 no-op 구현으로 교체 가능
interface ToolObservationPort {
    // Tool 실행 시작 — spanId 반환 (endSpan 호출 시 사용)
    fun startSpan(toolName: String, input: Map<String, Any>): String
    // Tool 실행 완료
    fun endSpan(spanId: String, output: String)
    // Tool 실행 실패
    fun endSpanWithError(spanId: String, error: String)
}
