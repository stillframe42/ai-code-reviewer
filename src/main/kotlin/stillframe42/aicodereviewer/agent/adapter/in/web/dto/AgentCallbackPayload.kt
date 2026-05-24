package stillframe42.aicodereviewer.agent.adapter.`in`.web.dto

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import stillframe42.aicodereviewer.agent.domain.model.AgentCallbackIssue
import stillframe42.aicodereviewer.agent.domain.model.AgentCallbackResult

// Python 에이전트 콜백 HTTP 요청 본문 — Jackson snake_case 매핑은 adapter 책임으로 격리.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AgentCallbackPayload(
    val analysisId: String,
    val status: String,
    val issues: List<AgentCallbackIssuePayload> = emptyList(),
    val error: String? = null,
) {
    fun toDomain(): AgentCallbackResult = AgentCallbackResult(
        analysisId = analysisId,
        status = status,
        issues = issues.map(AgentCallbackIssuePayload::toDomain),
        error = error,
    )
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AgentCallbackIssuePayload(
    val severity: String,
    val type: String,
    val location: String,
    val description: String,
    val suggestion: String,
    val owaspReference: String? = null,
) {
    fun toDomain(): AgentCallbackIssue = AgentCallbackIssue(
        severity = severity,
        type = type,
        location = location,
        description = description,
        suggestion = suggestion,
        owaspReference = owaspReference,
    )
}
