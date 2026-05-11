package stillframe42.aicodereviewer.agent.adapter.out.python

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.agent.adapter.out.python.dto.AgentAnalysisRequest
import stillframe42.aicodereviewer.agent.adapter.out.python.dto.AgentAnalysisResponse
import stillframe42.aicodereviewer.agent.adapter.out.python.dto.AgentIssue
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.model.AgentFinding
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.common.Logging
import java.util.UUID

@Component
class PythonAgentClient(
    @param:Value("\${agent.python.url:http://localhost:8081}")
    private val agentUrl: String,
) : AgentAnalysisPort, Logging {

    override suspend fun requestDeepAnalysis(
        command: AgentAnalysisCommand,
    ): AgentAnalysisResult {
        logger.info("Agent stub called for PR #{} (target={})", command.prNumber, agentUrl)
        val request = command.toDto()
        val response = stubResponseFor(request)
        return response.toDomain()
    }

    private fun stubResponseFor(request: AgentAnalysisRequest): AgentAnalysisResponse =
        AgentAnalysisResponse(
            analysisId = "stub-${UUID.randomUUID()}",
            status = "DONE",
            issues = listOf(
                AgentIssue(
                    severity = "INFO",
                    type = "STUB",
                    location = "stub:${request.prNumber}",
                    description = "Python 에이전트 연동 전 임시 응답",
                    suggestion = "DAY 15 이후 실제 에이전트 연동 예정",
                ),
            ),
        )

    override suspend fun getAnalysisResult(analysisId: String): AgentAnalysisResult =
        AgentAnalysisResponse(analysisId = analysisId, status = "DONE").toDomain()

    override suspend fun checkHealth(): Boolean = true
}

private fun AgentAnalysisCommand.toDto(): AgentAnalysisRequest =
    AgentAnalysisRequest(
        prNumber = prNumber,
        repo = repo,
        diff = diff,
        ragContext = ragContext,
        analysisType = analysisType,
        sessionId = sessionId,
    )

private fun AgentAnalysisResponse.toDomain(): AgentAnalysisResult =
    AgentAnalysisResult(
        analysisId = analysisId,
        status = status,
        findings = issues.map { it.toDomain() },
        error = error,
    )

private fun AgentIssue.toDomain(): AgentFinding =
    AgentFinding(
        severity = severity,
        type = type,
        location = location,
        description = description,
        suggestion = suggestion,
        owaspReference = owaspReference,
    )
