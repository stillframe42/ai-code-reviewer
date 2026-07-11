package stillframe42.aicodereviewer.agent.domain.model

// agent 경로 폴백 발생 도메인 이벤트 — 발행처는 리뷰 오케스트레이션, 수신처는 AgentMetricsEventListener.
// reason 은 unavailable / timeout / failed / error 4종으로 한정한다 (cardinality 안전)
data class AgentFallbackEvent(
    val reason: String,
)
