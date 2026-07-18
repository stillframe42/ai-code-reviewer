package stillframe42.aicodereviewer.common.langfuse

import kotlinx.coroutines.asContextElement

// 관측 세션 컨텍스트를 전파하기 위한 ThreadLocal 홀더
// asElement는 ReviewAdapter가 아직 코루틴 컨텍스트 요소로 전파할 때 사용한다 (Task 7 에서 제거 예정)
object ObservationSessionContextHolder {
    val local: ThreadLocal<ObservationSessionContext?> = ThreadLocal.withInitial { null }

    // 코루틴 컨텍스트 요소로 변환 — withContext에서 사용
    fun asElement(context: ObservationSessionContext?) = local.asContextElement(context)
}
