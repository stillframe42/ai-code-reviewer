package stillframe42.aicodereviewer.common.langfuse

// 현재 활성 부모 span 의 spanId 를 스레드 간 전파하는 홀더
// LangfuseTraceContextHolder 와 같은 ThreadLocal 패턴 — 병렬 전파는 TraceContextPropagation 이 담당한다
object LangfuseSpanContextHolder {
    private val current: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    fun set(spanId: String) = current.set(spanId)
    fun get(): String? = current.get()
    fun clear() = current.remove()
}
