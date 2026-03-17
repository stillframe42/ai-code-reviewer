package stillframe42.aicodereviewer.chat

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class DefaultChatService(
    private val chatClients: Map<AiProvider, ChatClient>
) : ChatService {

    // 지정된 AI 프로바이더에게 메시지를 전달하고 응답을 반환
    override fun chat(message: String, provider: AiProvider): String {
        val client = chatClients[provider]
            ?: throw IllegalArgumentException("지원하지 않는 AI 프로바이더입니다: $provider")
        return client.prompt()
            .user(message)
            .call()
            .content()
            ?: throw IllegalStateException("AI로부터 응답을 받지 못했습니다")
    }
}
