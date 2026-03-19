package stillframe42.aicodereviewer.config

import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import stillframe42.aicodereviewer.core.AiProvider

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

    // AiProvider → ChatClient 매핑 빈 (enum 키 Map은 Spring 자동 수집 불가 → 명시적 등록)
    @Bean
    fun chatClientMap(
        @Qualifier("anthropicChatClient") anthropicChatClient: ChatClient,
        @Qualifier("openAiChatClient") openAiChatClient: ChatClient,
    ): Map<AiProvider, ChatClient> = mapOf(
        AiProvider.ANTHROPIC to anthropicChatClient,
        AiProvider.OPENAI to openAiChatClient,
    )
}
