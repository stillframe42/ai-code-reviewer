package stillframe42.aicodereviewer.common

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.core.AiProvider

// AI 서비스에서 공통으로 사용하는 프롬프트 빌드 로직을 캡슐화한 헬퍼 컴포넌트
@Component
class AiPromptBuilder(private val chatClients: Map<AiProvider, ChatClient>) {

    fun build(
        systemPromptResource: Resource,
        userPromptResource: Resource,
        userVariables: Map<String, Any>,
        provider: AiProvider,
    ): ChatClient.ChatClientRequestSpec {
        val client = chatClients[provider]
            ?: throw IllegalArgumentException("지원하지 않는 AI 프로바이더입니다: $provider")
        val systemMsg = PromptTemplate(systemPromptResource).render()
        val userMsg = PromptTemplate(userPromptResource).render(userVariables)
        return client.prompt()
            .system(systemMsg)
            .user(userMsg)
    }
}
