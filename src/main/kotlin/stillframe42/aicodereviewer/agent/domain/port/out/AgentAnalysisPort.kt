package stillframe42.aicodereviewer.agent.domain.port.out

import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult

interface AgentAnalysisPort {
    fun requestDeepAnalysis(command: AgentAnalysisCommand): AgentAnalysisResult
    fun getAnalysisResult(analysisId: String): AgentAnalysisResult
    fun checkHealth(): Boolean
}
