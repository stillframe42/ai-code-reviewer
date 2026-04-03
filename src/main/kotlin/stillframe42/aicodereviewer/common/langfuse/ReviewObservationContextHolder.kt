package stillframe42.aicodereviewer.common.langfuse

import kotlinx.coroutines.asContextElement
import stillframe42.aicodereviewer.review.domain.model.ReviewContext

// 코루틴 경계를 넘어 ReviewContext를 전파하기 위한 ThreadLocal 홀더
// 사용: withContext(Dispatchers.IO + ReviewObservationContextHolder.asElement(context)) { ... }
object ReviewObservationContextHolder {
    val local: ThreadLocal<ReviewContext?> = ThreadLocal.withInitial { null }

    // 코루틴 컨텍스트 요소로 변환 — withContext에서 사용
    fun asElement(context: ReviewContext?) = local.asContextElement(context)
}
