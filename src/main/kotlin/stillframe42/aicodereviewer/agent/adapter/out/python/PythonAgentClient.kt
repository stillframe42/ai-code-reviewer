package stillframe42.aicodereviewer.agent.adapter.out.python

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import stillframe42.aicodereviewer.agent.adapter.out.python.dto.AgentAnalysisRequest
import stillframe42.aicodereviewer.agent.adapter.out.python.dto.AgentAnalysisResponse
import stillframe42.aicodereviewer.agent.adapter.out.python.dto.AgentIssue
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.model.AgentFinding
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.common.Logging

// HTTP 예외는 의도적으로 매핑하지 않는다 — 폴백/예외 분류는 호출자(서비스 레이어) 책임
@Component
class PythonAgentClient(
    @param:Qualifier("pythonAgentWebClient")
    private val webClient: WebClient,
) : AgentAnalysisPort, Logging {

    override suspend fun requestDeepAnalysis(
        command: AgentAnalysisCommand,
    ): AgentAnalysisResult {
        logger.info("Agent analyze requested for PR #{}", command.prNumber)
        val request = command.toDto()
        val response: AgentAnalysisResponse = webClient.post()
            .uri("/agent/analyze")
            .bodyValue(request)
            .retrieve()
            .awaitBody()
        return response.toDomain()
    }

    override suspend fun getAnalysisResult(analysisId: String): AgentAnalysisResult {
        val response: AgentAnalysisResponse = webClient.get()
            .uri("/agent/analyze/{id}", analysisId)
            .retrieve()
            .awaitBody()
        return response.toDomain()
    }

    override suspend fun checkHealth(): Boolean =
        runCatching {
            webClient.get().uri("/health").retrieve().awaitBodilessEntity()
        }.isSuccess
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
