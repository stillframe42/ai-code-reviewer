package stillframe42.aicodereviewer.chat.adapter.out.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.chat.domain.port.out.AiChatPort
import stillframe42.aicodereviewer.common.AiPromptBuilder
import stillframe42.aicodereviewer.common.advisor.CostTrackingAdvisor
import stillframe42.aicodereviewer.common.advisor.LoggingAdvisor
import stillframe42.aicodereviewer.common.advisor.RetryAdvisor
import stillframe42.aicodereviewer.common.exception.AiResponseException
import stillframe42.aicodereviewer.core.AiProvider

// Spring AI 기반 채팅 출력 어댑터 — AiChatPort 구현체
@Component
class ChatAdapter(
    private val promptBuilder: AiPromptBuilder,
    private val loggingAdvisor: LoggingAdvisor,
    private val retryAdvisor: RetryAdvisor,
    private val costTrackingAdvisor: CostTrackingAdvisor,

    @param:Value("\${app.prompt.chat-system}")
    private val systemPromptResource: Resource,

    @param:Value("\${app.prompt.chat-user}")
    private val userPromptResource: Resource,
) : AiChatPort {

    // 지정된 AI 프로바이더에게 메시지를 전달하고 응답을 반환 (코루틴 비동기)
    override suspend fun chat(message: String, provider: AiProvider, conventionContext: String?): String =
        // Spring AI blocking HTTP 호출을 IO 디스패처에서 격리 실행
        withContext(Dispatchers.IO) {
            promptBuilder.build(systemPromptResource, userPromptResource, buildVariables(message, conventionContext), provider)
                .advisors(loggingAdvisor, retryAdvisor, costTrackingAdvisor)
                .call()
                .content()
                ?: throw AiResponseException("AI로부터 응답을 받지 못했습니다")
        }

    // Spring AI streaming 호출: Flux<String> → Flow<String> 변환 (kotlinx-coroutines-reactor)
    // RetryAdvisor, CostTrackingAdvisor는 CallAdvisor만 구현하므로 스트리밍에서는 제외
    override fun streamChat(message: String, provider: AiProvider, conventionContext: String?): Flow<String> =
        promptBuilder.build(systemPromptResource, userPromptResource, buildVariables(message, conventionContext), provider)
            .advisors(loggingAdvisor)
            .stream()
            .content()
            .asFlow()

    // convention_section: 문서가 있으면 헤더+내용, 없으면 빈 문자열
    private fun buildVariables(message: String, conventionContext: String?): Map<String, Any> {
        val conventionSection = if (!conventionContext.isNullOrBlank()) {
            "[참고 컨벤션 문서]\n$conventionContext\n"
        } else ""
        return mapOf(
            "message" to message,
            "convention_section" to conventionSection,
        )
    }
}
