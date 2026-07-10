package stillframe42.aicodereviewer.common.langfuse

import kotlinx.coroutines.asContextElement

// 코루틴 경계를 넘어 관측 세션 컨텍스트를 전파하기 위한 ThreadLocal 홀더
// 사용: withContext(Dispatchers.IO + ObservationSessionContextHolder.asElement(context)) { ... }
object ObservationSessionContextHolder {
    val local: ThreadLocal<ObservationSessionContext?> = ThreadLocal.withInitial { null }

    // 코루틴 컨텍스트 요소로 변환 — withContext에서 사용
    fun asElement(context: ObservationSessionContext?) = local.asContextElement(context)
}
