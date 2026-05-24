package stillframe42.aicodereviewer.agent.domain.model

// 원격 에이전트가 분석 완료 시 콜백으로 전달하는 결과 — 순수 도메인 모델 (직렬화 의존 없음)
data class AgentCallbackResult(
    val analysisId: String,
    val status: String,
    val issues: List<AgentCallbackIssue> = emptyList(),
    val error: String? = null,
)

data class AgentCallbackIssue(
    val severity: String,
    val type: String,
    val location: String,
    val description: String,
    val suggestion: String,
    val owaspReference: String? = null,
)
