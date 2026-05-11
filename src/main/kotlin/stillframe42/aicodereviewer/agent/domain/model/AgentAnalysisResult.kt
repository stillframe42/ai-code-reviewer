package stillframe42.aicodereviewer.agent.domain.model

data class AgentAnalysisResult(
    val analysisId: String,
    val status: String,
    val findings: List<AgentFinding> = emptyList(),
    val error: String? = null,
)
