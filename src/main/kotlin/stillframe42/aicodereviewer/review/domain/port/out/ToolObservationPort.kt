package stillframe42.aicodereviewer.review.domain.port.out

/**
 * Tool 실행 추적 아웃바운드 포트 — Langfuse Span 기록 또는 no-op 구현으로 교체 가능.
 *
 * **계약**: 구현체는 메인 플로우를 방해하지 않도록 내부에서 예외를 억제해야 한다.
 * 전송 실패 등 관측 오류는 로깅으로만 처리하고 호출자에게 예외를 전파하지 않는다.
 */
interface ToolObservationPort {
    // Tool 실행 시작 — spanId 반환 (endSpan 호출 시 사용). 전송 실패 시 빈 문자열 반환
    fun startSpan(toolName: String, input: Map<String, Any>): String
    // Tool 실행 완료
    fun endSpan(spanId: String, output: String)
    // Tool 실행 실패
    fun endSpanWithError(spanId: String, error: String)
}
