package stillframe42.aicodereviewer.common.langfuse

import kotlinx.coroutines.asContextElement

// LangfuseObservationHandler.onStart()에서 설정한 traceId를 Tool 실행 시점까지 전달하는 ThreadLocal 홀더
// SpringAiReviewAdapter에서 asContextElement()를 통해 코루틴 컨텍스트로 전파하므로
// withContext 내의 모든 코루틴/스레드에서 동일한 traceId에 접근할 수 있다
object LangfuseTraceContextHolder {
    private val current: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    fun set(traceId: String) = current.set(traceId)
    fun get(): String? = current.get()
    fun clear() = current.remove()

    // 코루틴 컨텍스트 요소로 변환 — withContext에서 사용
    fun asElement(traceId: String?) = current.asContextElement(traceId)
}
