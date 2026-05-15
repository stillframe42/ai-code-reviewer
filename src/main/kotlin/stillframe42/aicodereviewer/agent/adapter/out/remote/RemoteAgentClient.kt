package stillframe42.aicodereviewer.agent.adapter.out.remote

import java.net.ConnectException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import stillframe42.aicodereviewer.agent.adapter.out.remote.dto.AgentAnalysisRequest
import stillframe42.aicodereviewer.agent.adapter.out.remote.dto.AgentAnalysisResponse
import stillframe42.aicodereviewer.agent.adapter.out.remote.dto.AgentIssue
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.model.AgentFinding
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.common.Logging

// 네트워크/5xx 는 AgentUnavailableException 으로 매핑 — 호출자(서비스 레이어)가 폴백 분기에서
// 도메인 예외 한 번에 처리하도록. 4xx 는 호출자 버그이므로 재전파 (디버그 가능성 유지).
@Component
class RemoteAgentClient(
    @param:Qualifier("remoteAgentWebClient")
    private val webClient: WebClient,
) : AgentAnalysisPort, Logging {

    override suspend fun requestDeepAnalysis(
        command: AgentAnalysisCommand,
    ): AgentAnalysisResult = mapHttpExceptions {
        logger.info(
            "Agent analyze requested: pr={}, ragChunks={}, ragChars={}, diffChars={}",
            command.prNumber,
            command.contextIds.size,
            command.contextIds.sumOf { it.length },
            command.diff.length,
        )
        val request = command.toDto()
        val response: AgentAnalysisResponse = webClient.post()
            .uri("/agent/analyze")
            .bodyValue(request)
            .retrieve()
            .awaitBody()
        response.toDomain()
    }

    override suspend fun getAnalysisResult(analysisId: String): AgentAnalysisResult = mapHttpExceptions {
        val response: AgentAnalysisResponse = webClient.get()
            .uri("/agent/analyze/{id}", analysisId)
            .retrieve()
            .awaitBody()
        response.toDomain()
    }

    override suspend fun checkHealth(): Boolean =
        runCatching {
            webClient.get().uri("/health").retrieve().awaitBodilessEntity()
        }.isSuccess

    // WebClient 네트워크/5xx 예외만 AgentUnavailableException 으로 변환한다.
    // 4xx 는 호출자(우리) 의 버그이므로 그대로 위로 던진다.
    private suspend fun <T> mapHttpExceptions(block: suspend () -> T): T =
        try {
            block()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.is5xxServerError) {
                throw AgentUnavailableException("HTTP ${e.statusCode.value()}", e)
            }
            throw e
        } catch (e: WebClientRequestException) {
            throw AgentUnavailableException("network: ${e.message}", e)
        } catch (e: ConnectException) {
            throw AgentUnavailableException("connect failed: ${e.message}", e)
        }
}

private fun AgentAnalysisCommand.toDto(): AgentAnalysisRequest =
    AgentAnalysisRequest(
        prNumber = prNumber,
        repo = repo,
        diff = diff,
        contextIds = contextIds,
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
