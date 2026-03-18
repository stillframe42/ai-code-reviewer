package stillframe42.aicodereviewer.chat

import kotlinx.coroutines.Dispatchers
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

    // 지정된 AI 프로바이더에게 프롬프트 템플릿을 적용하여 메시지를 전달하고 응답을 반환 (코루틴 비동기)
    override suspend fun chat(message: String, provider: AiProvider): String {
        val client = chatClients[provider]
            ?: throw IllegalArgumentException("지원하지 않는 AI 프로바이더입니다: $provider")

        // CPU 연산인 프롬프트 렌더링은 IO 블록 밖에서 처리
        val systemMsg = PromptTemplate(systemPromptResource).render()
        val userMsg = PromptTemplate(userPromptResource).render(mapOf("message" to message))

        // Spring AI blocking HTTP 호출을 IO 디스패처에서 격리 실행
        return withContext(Dispatchers.IO) {
            client.prompt()
                .system(systemMsg)
                .user(userMsg)
                .call()
                .content()
                ?: throw IllegalStateException("AI로부터 응답을 받지 못했습니다")
        }
    }
}
