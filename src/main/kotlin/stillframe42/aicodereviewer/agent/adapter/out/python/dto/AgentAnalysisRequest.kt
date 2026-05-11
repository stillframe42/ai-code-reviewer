package stillframe42.aicodereviewer.agent.adapter.out.python.dto

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AgentAnalysisRequest(
    val prNumber: Int,
    val repo: String,
    val diff: String,
    val ragContext: List<String> = emptyList(),
    val analysisType: String = "GENERAL",
    val sessionId: String? = null,
)
