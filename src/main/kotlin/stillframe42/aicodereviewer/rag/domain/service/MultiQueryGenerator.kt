package stillframe42.aicodereviewer.rag.domain.service

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

// 원본 쿼리(파일명)를 LLM으로 3가지 변형으로 확장하여 검색 다양성을 확보한다.
// 변형 실패 시 원본만 반환 (graceful degradation).
@Component
class MultiQueryGenerator(
    @param:Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    @param:Value("classpath:prompts/rag-multi-query.st") private val promptResource: Resource,
    private val objectMapper: ObjectMapper,
) : Logging {

    // 원본 쿼리 + LLM 변형 3개 = 최대 4개 반환 (중복 제거)
    suspend fun generateMultipleQueries(originalQuery: String): List<String> {
        val prompt = promptResource.getContentAsString(Charsets.UTF_8)
            .replace("{originalQuery}", originalQuery)
        return runCatching {
            val raw = withContext(Dispatchers.IO) {
                chatClient.prompt()
                    .system(prompt)
                    .user("위 지침에 따라 변형을 생성하세요.")
                    .options(AnthropicChatOptions.builder().temperature(0.0).build())
                    .call()
                    .content()
                    ?.trim()
                    ?: ""
            }
            val map: Map<String, List<String>> = objectMapper.readValue(extractJson(raw))
            val variants = map["variants"] ?: emptyList()
            (listOf(originalQuery) + variants).distinct()
        }.getOrElse { e ->
            logger.warn("Multi-query 생성 실패, 원본만 반환: query={}, error={}", originalQuery, e.message)
            listOf(originalQuery)
        }
    }

    // LLM 응답에서 JSON 추출 — 코드 펜스/앞뒤 텍스트 제거
    private fun extractJson(raw: String): String {
        val stripped = raw.replace(Regex("```(json)?\\s*"), "").trim()
        val start = stripped.indexOf('{')
        if (start < 0) return stripped
        val end = stripped.lastIndexOf('}')
        return if (end > start) stripped.substring(start, end + 1) else stripped
    }
}
