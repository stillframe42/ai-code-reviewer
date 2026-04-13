package stillframe42.aicodereviewer.common

import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.core.AiProvider

// AI 서비스에서 공통으로 사용하는 프롬프트 빌드 로직을 캡슐화한 헬퍼 컴포넌트
// ST4(StringTemplate4) 대신 단순 문자열 치환을 사용한다 —
// 코드는 어떤 문자든 포함할 수 있으므로 ST4 구분자가 무엇이든 충돌 가능성이 있다
@Component
class AiPromptBuilder(private val chatClients: Map<AiProvider, ChatClient>) {

    fun build(
        systemPromptResource: Resource,
        userPromptResource: Resource,
        variables: Map<String, Any>,
        provider: AiProvider,
    ): ChatClient.ChatClientRequestSpec {
        val client = chatClients[provider]
            ?: throw IllegalArgumentException("지원하지 않는 AI 프로바이더입니다: $provider")
        val systemMsg = renderTemplate(systemPromptResource.getContentAsString(Charsets.UTF_8), variables)
        val userMsg = renderTemplate(userPromptResource.getContentAsString(Charsets.UTF_8), variables)
        return client.prompt()
            .system(systemMsg)
            .user(userMsg)
    }

    // {key} 형태의 플레이스홀더를 단순 문자열 치환으로 렌더링한다
    private fun renderTemplate(template: String, variables: Map<String, Any>): String =
        variables.entries.fold(template) { acc, (key, value) ->
            acc.replace("{$key}", value.toString())
        }
}
