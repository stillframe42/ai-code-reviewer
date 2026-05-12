package stillframe42.aicodereviewer.agent.domain.exception

class AgentAnalysisFailedException(
    val analysisId: String,
    val agentError: String?,
) : RuntimeException("Agent analysis failed: id=$analysisId, error=$agentError")
