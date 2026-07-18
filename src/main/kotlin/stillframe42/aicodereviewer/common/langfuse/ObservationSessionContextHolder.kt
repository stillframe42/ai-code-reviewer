package stillframe42.aicodereviewer.common.langfuse

// 관측 세션 컨텍스트를 전파하기 위한 ThreadLocal 홀더
object ObservationSessionContextHolder {
    val local: ThreadLocal<ObservationSessionContext?> = ThreadLocal.withInitial { null }
}
