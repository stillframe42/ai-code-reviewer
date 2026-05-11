package stillframe42.aicodereviewer.agent.adapter.out.python.dto

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AgentAnalysisResponse(
    val analysisId: String,
    val status: String,
    val issues: List<AgentIssue> = emptyList(),
    val error: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AgentIssue(
    val severity: String,
    val type: String,
    val location: String,
    val description: String,
    val suggestion: String,
    val owaspReference: String? = null,
)
