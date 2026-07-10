package stillframe42.aicodereviewer.common.langfuse

// LLM 관측 이벤트에 부착할 세션 식별자와 metadata — feature 가 자신의 도메인 컨텍스트를 변환해 전달한다
data class ObservationSessionContext(
    val sessionId: String?,
    val metadata: Map<String, String>,
)
