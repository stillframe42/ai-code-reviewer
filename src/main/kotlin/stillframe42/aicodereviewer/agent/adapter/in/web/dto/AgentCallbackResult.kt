package stillframe42.aicodereviewer.agent.adapter.`in`.web.dto

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AgentCallbackResult(
    val analysisId: String,
    val status: String,
    val issues: List<AgentCallbackIssue> = emptyList(),
    val error: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AgentCallbackIssue(
    val severity: String,
    val type: String,
    val location: String,
    val description: String,
    val suggestion: String,
    val owaspReference: String? = null,
)
