package stillframe42.aicodereviewer.agent.domain.model

data class AgentFinding(
    val severity: String,
    val type: String,
    val location: String,
    val description: String,
    val suggestion: String,
    val owaspReference: String? = null,
)
