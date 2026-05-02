package stillframe42.aicodereviewer.common.langfuse

import kotlinx.coroutines.asContextElement

// 현재 활성 부모 span 의 spanId 를 코루틴 간 전파하는 홀더
// LangfuseTraceContextHolder 와 같은 ThreadLocal + asContextElement 패턴
object LangfuseSpanContextHolder {
    private val current: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    fun get(): String? = current.get()

    fun asElement(spanId: String?) = current.asContextElement(spanId)
}
