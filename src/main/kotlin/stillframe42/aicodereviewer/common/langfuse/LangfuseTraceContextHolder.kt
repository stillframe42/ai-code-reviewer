package stillframe42.aicodereviewer.common.langfuse

// LangfuseObservationHandler.onStart()에서 설정한 traceId를 Tool 실행 시점까지 전달하는 ThreadLocal 홀더
// call()이 Dispatchers.IO에서 블로킹으로 실행되므로 onStart→Tool 실행→onStop이 같은 스레드에서 발생 → 안전
object LangfuseTraceContextHolder {
    private val current: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    fun set(traceId: String) = current.set(traceId)
    fun get(): String? = current.get()
    fun clear() = current.remove()
}
