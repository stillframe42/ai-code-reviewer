package stillframe42.aicodereviewer.config

import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import stillframe42.aicodereviewer.core.AiProvider

@Configuration
class ChatClientConfig {

    // Anthropic Claude 기반 ChatClient 빈 — ObservationRegistry 연결로 Langfuse 추적 활성화
    @Bean("anthropicChatClient")
    fun anthropicChatClient(
        anthropicChatModel: AnthropicChatModel,
        observationRegistry: ObservationRegistry,
    ): ChatClient =
        // ChatClientObservationConvention = null (기본값 사용), AdvisorObservationConvention = null (기본값 사용)
        ChatClient.builder(anthropicChatModel, observationRegistry, null, null)
            .build()

    // OpenAI 기반 ChatClient 빈 — ObservationRegistry 연결로 Langfuse 추적 활성화
    @Bean("openAiChatClient")
    fun openAiChatClient(
        openAiChatModel: OpenAiChatModel,
        observationRegistry: ObservationRegistry,
    ): ChatClient =
        // ChatClientObservationConvention = null (기본값 사용), AdvisorObservationConvention = null (기본값 사용)
        ChatClient.builder(openAiChatModel, observationRegistry, null, null)
            .build()

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
