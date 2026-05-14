package stillframe42.aicodereviewer.agent.domain.model

data class AgentAnalysisCommand(
    val prNumber: Int,
    val repo: String,
    val diff: String,
    val contextIds: List<String> = emptyList(),
    val analysisType: String = "GENERAL",
    val sessionId: String? = null,
)
