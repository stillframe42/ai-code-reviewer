package stillframe42.aicodereviewer.agent.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.agent.domain.service.AgentFindingMapper
import stillframe42.aicodereviewer.agent.domain.service.SecurityFileDetector
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.rag.application.ConventionContextService
import stillframe42.aicodereviewer.review.domain.model.CodeReview

@Service
class AgentReviewService(
    private val agentAnalysisPort: AgentAnalysisPort,
    private val agentPoller: AgentPoller,
    private val conventionContextService: ConventionContextService,
) {

    suspend fun review(
        repositoryFullName: String,
        pullRequestNumber: Int,
        prDiff: String,
        prFiles: List<PrFile>,
        reviewRequestId: Long?,
    ): CodeReview {
        val securityFile = SecurityFileDetector.firstSecurityFile(prFiles)
        val contextIds = conventionContextService.buildContextIds(
            query = SECURITY_QUERY,
            filePath = securityFile?.filename,
        )

        val command = AgentAnalysisCommand(
            prNumber = pullRequestNumber,
            repo = repositoryFullName,
            diff = prDiff,
            contextIds = contextIds,
            analysisType = "SECURITY",
            sessionId = reviewRequestId?.toString(),
        )

        val initial = agentAnalysisPort.requestDeepAnalysis(command)
        val result = agentPoller.pollUntilComplete(initial.analysisId)

        return AgentFindingMapper.toCodeReview(result)
    }

    companion object {
        private const val SECURITY_QUERY = "security review for changed files"
    }
}
