package stillframe42.aicodereviewer.common.langfuse

// 부모 스레드의 트레이스 ThreadLocal 3종을 자식 태스크로 복사한다 — 병렬 실행 시
// 코루틴 asContextElement 가 하던 역할의 동기 버전. 자식 스레드는 실행 후 반드시 정리해
// 캐리어/풀 스레드 재사용 시 오염을 방지한다.
object TraceContextPropagation {
    fun <T> capture(block: () -> T): () -> T {
        val traceId = LangfuseTraceContextHolder.get()
        val spanId = LangfuseSpanContextHolder.get()
        val session = ObservationSessionContextHolder.local.get()
        return {
            try {
                traceId?.let(LangfuseTraceContextHolder::set)
                spanId?.let(LangfuseSpanContextHolder::set)
                session?.let(ObservationSessionContextHolder.local::set)
                block()
            } finally {
                LangfuseTraceContextHolder.clear()
                LangfuseSpanContextHolder.clear()
                ObservationSessionContextHolder.local.remove()
            }
        }
    }
}
