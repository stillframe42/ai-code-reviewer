package stillframe42.aicodereviewer.common.observability

import kotlinx.coroutines.withContext

// span 라이프사이클을 try/finally 로 관리하는 유틸
// 정상 완료 시 endSpan(output 람다), 예외 시 endSpanWithError 자동 호출
suspend inline fun <T> ObservabilityPort.withSpan(
    name: String,
    input: Map<String, Any> = emptyMap(),
    metadata: Map<String, Any> = emptyMap(),
    crossinline outputMapper: (T) -> Map<String, Any> = { emptyMap() },
    crossinline block: suspend () -> T,
): T {
    val handle = startSpan(name, input, metadata)
    return try {
        val result = withContext(spanContext(handle.spanId)) { block() }
        endSpan(handle, outputMapper(result), metadata)
        result
    } catch (e: Throwable) {
        endSpanWithError(handle, e.message ?: e.javaClass.simpleName)
        throw e
    }
}
