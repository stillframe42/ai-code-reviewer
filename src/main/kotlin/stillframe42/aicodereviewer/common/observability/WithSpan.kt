package stillframe42.aicodereviewer.common.observability

// span 라이프사이클을 try/finally 로 관리하는 유틸
// 정상 완료 시 endSpan(output 람다), 예외 시 endSpanWithError 자동 호출
// block 은 non-crossinline — suspend 호출부에 인라인되어 마이그레이션 중간 상태에서도 사용 가능
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
