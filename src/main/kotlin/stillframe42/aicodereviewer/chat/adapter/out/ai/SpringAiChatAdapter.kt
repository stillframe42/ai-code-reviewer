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
import stillframe42.aicodereviewer.core.AiProvider

// Spring AI 기반 채팅 출력 어댑터 — AiChatPort 구현체
@Component
class SpringAiChatAdapter(
    private val promptBuilder: AiPromptBuilder,

    @param:Value("\${app.prompt.chat-system}")
    private val systemPromptResource: Resource,

    @param:Value("\${app.prompt.chat-user}")
    private val userPromptResource: Resource,
) : AiChatPort {

    // 지정된 AI 프로바이더에게 메시지를 전달하고 응답을 반환 (코루틴 비동기)
    override suspend fun chat(message: String, provider: AiProvider): String =
        // Spring AI blocking HTTP 호출을 IO 디스패처에서 격리 실행
        withContext(Dispatchers.IO) {
            promptBuilder.build(systemPromptResource, userPromptResource, mapOf("message" to message), provider)
                .call()
                .content()
                ?: throw IllegalStateException("AI로부터 응답을 받지 못했습니다")
        }

    // Spring AI streaming 호출: Flux<String> → Flow<String> 변환 (kotlinx-coroutines-reactor)
    override fun streamChat(message: String, provider: AiProvider): Flow<String> =
        promptBuilder.build(systemPromptResource, userPromptResource, mapOf("message" to message), provider)
            .stream()
            .content()
            .asFlow()
}
