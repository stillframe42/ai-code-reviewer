package stillframe42.aicodereviewer.agent.domain.exception

class AgentAnalysisTimeoutException(
    val analysisId: String,
    reason: String,
    cause: Throwable? = null,
) : RuntimeException("Agent analysis timeout: id=$analysisId ($reason)", cause)
