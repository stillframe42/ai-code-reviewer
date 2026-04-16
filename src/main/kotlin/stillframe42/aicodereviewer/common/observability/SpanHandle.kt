package stillframe42.aicodereviewer.common.observability

// span 시작/종료 간 식별자 전달용 — startSpan 반환값을 endSpan/endSpanWithError에 전달
data class SpanHandle(
    val spanId: String,
    val traceId: String,
)
