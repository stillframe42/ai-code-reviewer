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
    private val conventionContextService: ConventionContextService,
) {

    suspend fun review(
        repositoryFullName: String,
        pullRequestNumber: Int,
        prDiff: String,
        prFiles: List<PrFile>,
    ): CodeReview {
        val securityFile = SecurityFileDetector.firstSecurityFile(prFiles)
        val ragContext = listOf(
            conventionContextService.buildContext(
                query = SECURITY_QUERY,
                filePath = securityFile?.filename,
            ),
        )

        val command = AgentAnalysisCommand(
            prNumber = pullRequestNumber,
            repo = repositoryFullName,
            diff = prDiff,
            ragContext = ragContext,
            analysisType = "SECURITY",
        )

        val result = agentAnalysisPort.requestDeepAnalysis(command)

        if (result.error != null) {
            error("Agent analysis returned error: ${result.error}")
        }

        return AgentFindingMapper.toCodeReview(result)
    }

    companion object {
        private const val SECURITY_QUERY = "security review for changed files"
    }
}
