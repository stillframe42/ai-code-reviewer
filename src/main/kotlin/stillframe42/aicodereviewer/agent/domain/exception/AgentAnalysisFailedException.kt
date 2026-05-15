package stillframe42.aicodereviewer.agent.domain.exception

class AgentAnalysisFailedException(
    val analysisId: String,
    val agentError: String?,
) : AgentException("Agent analysis failed: id=$analysisId, error=$agentError")
