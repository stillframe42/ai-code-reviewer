package stillframe42.aicodereviewer.common.langfuse

import kotlinx.coroutines.asContextElement

// LangfuseObservationHandler.onStart()에서 설정한 traceId를 Tool 실행 시점까지 전달하는 ThreadLocal 홀더
// asElement는 ReviewAdapter가 아직 코루틴 컨텍스트 요소로 전파할 때 사용한다 (Task 7 에서 제거 예정)
object LangfuseTraceContextHolder {
    private val current: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    fun set(traceId: String) = current.set(traceId)
    fun get(): String? = current.get()
    fun clear() = current.remove()

    // 코루틴 컨텍스트 요소로 변환 — withContext에서 사용
    fun asElement(traceId: String?) = current.asContextElement(traceId)
}
