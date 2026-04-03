package stillframe42.aicodereviewer.common.advisor

import org.springframework.ai.chat.client.ChatClientMessageAggregator
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain
import org.springframework.core.Ordered
import reactor.core.publisher.Flux
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.TokenEstimator

// AI 호출 시 토큰 수와 소요 시간을 로그로 기록하는 어드바이저
class LoggingAdvisor(
    private val order: Int = Ordered.HIGHEST_PRECEDENCE,
) : CallAdvisor, StreamAdvisor, Logging {

    override fun getName(): String = "LoggingAdvisor"
    override fun getOrder(): Int = order

    override fun adviseCall(
        request: ChatClientRequest,
        chain: CallAdvisorChain,
    ): ChatClientResponse {
        val startTime = System.currentTimeMillis()
        val response = chain.nextCall(request)
        val elapsedMs = System.currentTimeMillis() - startTime
        logResponse(request, response, elapsedMs)
        return response
    }

    override fun adviseStream(
        request: ChatClientRequest,
        chain: StreamAdvisorChain,
    ): Flux<ChatClientResponse> {
        val startTime = System.currentTimeMillis()
        return ChatClientMessageAggregator()
            .aggregateChatClientResponse(chain.nextStream(request)) { aggregated ->
                val elapsedMs = System.currentTimeMillis() - startTime
                logResponse(request, aggregated, elapsedMs)
            }
    }

    // 응답 메타데이터(모델, 토큰 수, 소요 시간, 종료 이유)를 INFO 레벨로 기록
    private fun logResponse(request: ChatClientRequest, response: ChatClientResponse, elapsedMs: Long) {
        try {
            val model = response.chatResponse()?.metadata?.model ?: "unknown"
            val finishReason = response.chatResponse()?.result?.metadata?.finishReason ?: "unknown"
            val tokens = extractTokens(request, response)
            logger.info(
                "[LLM] {} | prompt={}tok | completion={}tok | {}ms | finish={}",
                model, tokens.prompt, tokens.completion, elapsedMs, finishReason,
            )
        } catch (e: Exception) {
            logger.warn("로깅 중 오류 발생 (무시)", e)
        }
    }

    // usage가 있으면 실제 토큰 수, 없으면 TokenEstimator로 추정값 반환
    private fun extractTokens(request: ChatClientRequest, response: ChatClientResponse): TokenInfo {
        val usage = response.chatResponse()?.metadata?.usage
        return TokenInfo(
            prompt = usage?.promptTokens ?: TokenEstimator.estimate(
                request.prompt().instructions.joinToString(" ") { it.text.orEmpty() },
            ),
            completion = usage?.completionTokens ?: TokenEstimator.estimate(
                response.chatResponse()?.result?.output?.text.orEmpty(),
            ),
        )
    }
}

// 토큰 수 정보를 담는 내부 데이터 클래스
private data class TokenInfo(val prompt: Int, val completion: Int)
