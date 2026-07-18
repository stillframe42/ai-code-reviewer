package stillframe42.aicodereviewer.common.observability

// span 라이프사이클을 try/finally 로 관리하는 유틸
// 정상 완료 시 endSpan(output 람다), 예외 시 endSpanWithError 자동 호출
// block 은 non-crossinline — 호출부에서 return@withSpan 같은 non-local return 사용 가능
inline fun <T> ObservabilityPort.withSpan(
    name: String,
    input: Map<String, Any> = emptyMap(),
    metadata: Map<String, Any> = emptyMap(),
    outputMapper: (T) -> Map<String, Any> = { emptyMap() },
    block: () -> T,
): T {
    val handle = startSpan(name, input, metadata)
    val previousSpanId = currentSpanId()
    if (handle.spanId.isNotEmpty()) activateSpan(handle.spanId)
    try {
        val result = block()
        endSpan(handle, outputMapper(result), metadata)
        return result
    } catch (e: Throwable) {
        endSpanWithError(handle, e.message ?: e.javaClass.simpleName)
        throw e
    } finally {
        activateSpan(previousSpanId)
    }
}
