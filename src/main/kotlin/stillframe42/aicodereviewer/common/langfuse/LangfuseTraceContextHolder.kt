package stillframe42.aicodereviewer.common.langfuse

// LangfuseObservationHandler.onStart()에서 설정한 traceId를 Tool 실행 시점까지 전달하는 ThreadLocal 홀더
object LangfuseTraceContextHolder {
    private val current: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    fun set(traceId: String) = current.set(traceId)
    fun get(): String? = current.get()
    fun clear() = current.remove()
}
