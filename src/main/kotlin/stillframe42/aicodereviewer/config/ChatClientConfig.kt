package stillframe42.aicodereviewer.config

import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatClientConfig {

    // Anthropic Claude 기반 ChatClient 빈
    @Bean("anthropicChatClient")
    fun anthropicChatClient(anthropicChatModel: AnthropicChatModel): ChatClient =
        ChatClient.create(anthropicChatModel)

    // OpenAI 기반 ChatClient 빈
    @Bean("openAiChatClient")
    fun openAiChatClient(openAiChatModel: OpenAiChatModel): ChatClient =
        ChatClient.create(openAiChatModel)
}
