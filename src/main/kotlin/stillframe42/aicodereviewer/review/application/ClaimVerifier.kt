package stillframe42.aicodereviewer.review.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.review.domain.model.CodeIssue

// 리뷰 응답의 모든 issues를 한 번의 LLM 호출에 묶어 검증 (batching).
// rejected는 결과에서 필터링 (Faithfulness 개선 목적).
@Component
class ClaimVerifier(
    @param:Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    @param:Value("classpath:prompts/review/review-claim-verification.st") private val promptResource: Resource,
    private val objectMapper: ObjectMapper,
) : Logging {

    suspend fun verify(issues: List<CodeIssue>, context: String): List<CodeIssue> {
        if (issues.isEmpty() || context.isBlank()) return issues
        val issuesText = issues.joinToString("\n") { "${it.id}: [${it.severity}] ${it.description}" }
        val prompt = promptResource.getContentAsString(Charsets.UTF_8)
            .replace("{context}", context)
            .replace("{issues}", issuesText)

        return runCatching {
            val raw = withContext(Dispatchers.IO) {
                chatClient.prompt()
                    .system(prompt)
                    .user("위 지침에 따라 검증하고 JSON으로 응답하세요.")
                    .options(AnthropicChatOptions.builder().temperature(0.0))
                    .call()
                    .content()
                    ?.trim()
                    ?: ""
            }
            val verdict: Map<String, Any> = objectMapper.readValue(extractJson(raw))
            @Suppress("UNCHECKED_CAST")
            val verifiedIds = (verdict["verified"] as? List<String>)?.toSet() ?: emptySet()
            @Suppress("UNCHECKED_CAST")
            val rejectedItems = (verdict["rejected"] as? List<Map<String, String>>) ?: emptyList()

            rejectedItems.forEach { r ->
                logger.info("ClaimVerifier rejected: id={}, reason={}", r["id"], r["reason"])
            }

            issues.filter { it.id in verifiedIds }
        }.getOrElse { e ->
            logger.warn("ClaimVerifier 검증 실패, 원본 이슈 그대로 반환: error={}", e.message)
            issues
        }
    }

    private fun extractJson(raw: String): String {
        val stripped = raw.replace(Regex("```(json)?\\s*"), "").trim()
        val start = stripped.indexOf('{')
        if (start < 0) return stripped
        val end = stripped.lastIndexOf('}')
        return if (end > start) stripped.substring(start, end + 1) else stripped
    }
}
