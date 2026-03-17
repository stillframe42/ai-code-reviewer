package stillframe42.aicodereviewer.chat

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class ChatService(
    @param:Qualifier("anthropicChatClient") private val chatClient: ChatClient
) {

    // Anthropic Claude에게 메시지를 전달하고 응답을 반환
    fun chat(message: String): String =
        chatClient.prompt()
            .user(message)
            .call()
            .content()
            ?: throw IllegalStateException("AI로부터 응답을 받지 못했습니다")
}
