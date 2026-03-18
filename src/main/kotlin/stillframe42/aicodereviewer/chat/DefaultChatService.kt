package stillframe42.aicodereviewer.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
class DefaultChatService(
    private val chatClients: Map<AiProvider, ChatClient>,

    @param:Value("classpath:prompts/chat-system.st")
    private val systemPromptResource: Resource,

    @param:Value("classpath:prompts/chat-user.st")
    private val userPromptResource: Resource,
) : ChatService {

    // 공통 로직: 클라이언트 조회 + 시스템/유저 프롬프트 빌드
    private fun buildPromptSpec(message: String, provider: AiProvider): ChatClient.ChatClientRequestSpec {
        val client = chatClients[provider]
            ?: throw IllegalArgumentException("지원하지 않는 AI 프로바이더입니다: $provider")
        val systemMsg = PromptTemplate(systemPromptResource).render()
        val userMsg = PromptTemplate(userPromptResource).render(mapOf("message" to message))
        return client.prompt()
            .system(systemMsg)
            .user(userMsg)
    }

    // 지정된 AI 프로바이더에게 메시지를 전달하고 응답을 반환 (코루틴 비동기)
    override suspend fun chat(message: String, provider: AiProvider): String =
        // Spring AI blocking HTTP 호출을 IO 디스패처에서 격리 실행
        withContext(Dispatchers.IO) {
            buildPromptSpec(message, provider)
                .call()
                .content()
                ?: throw IllegalStateException("AI로부터 응답을 받지 못했습니다")
        }

    // Spring AI streaming 호출: Flux<String> → Flow<String> 변환 (kotlinx-coroutines-reactor)
    override fun streamChat(message: String, provider: AiProvider): Flow<String> =
        buildPromptSpec(message, provider)
            .stream()
            .content()
            .asFlow()
}
