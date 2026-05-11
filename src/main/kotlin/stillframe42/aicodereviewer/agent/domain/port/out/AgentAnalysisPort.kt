package stillframe42.aicodereviewer.agent.domain.port.out

import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult

interface AgentAnalysisPort {
    suspend fun requestDeepAnalysis(command: AgentAnalysisCommand): AgentAnalysisResult
    suspend fun getAnalysisResult(analysisId: String): AgentAnalysisResult
    suspend fun checkHealth(): Boolean
}
