package stillframe42.aicodereviewer.rag.adapter.out.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.RagCompressionProperties
import stillframe42.aicodereviewer.rag.domain.port.out.ContextCompressorPort

// gpt-4o-mini 기반 추출 압축 어댑터 — ContextCompressorPort 구현
// Phase 1 인사이트 반영: 임계값 이하 청크는 LLM 호출 없이 우회
// per-chunk fail-open: 한 청크 압축 실패 시 해당 청크는 원본 그대로 통과
@Component
class LlmContextCompressorAdapter(
    @param:Qualifier("openAiChatClient") private val chatClient: ChatClient,
    private val properties: RagCompressionProperties,
    @param:Value("classpath:prompts/context-compressor.st")
    private val promptResource: Resource,
) : ContextCompressorPort, Logging {

    // cl100k_base 토큰 카운터 — 압축 임계값 판정용
    private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator()

    override suspend fun compress(
        query: String,
        documents: List<Document>,
    ): List<Document> = documents.mapNotNull { doc -> compressOne(query, doc) }

    // 단일 청크 압축 — 임계값 이하면 우회, 초과면 LLM 호출, 실패 시 원본 fallback, 빈/NONE 결과는 null
    private suspend fun compressOne(query: String, doc: Document): Document? {
        val text = doc.text ?: return null
        val tokens = tokenEstimator.estimate(text)

        // 작은 청크는 압축 우회 (Phase 1: 작은 청크 = 단일 토픽 = 노이즈 적음)
        if (tokens <= properties.compressionThresholdTokens) return doc

        return runCatching {
            val compressed = withContext(Dispatchers.IO) { callCompressor(query, text) }
            when {
                compressed.isBlank() -> null
                compressed == "NONE" -> null
                else -> Document.builder()
                    .text(compressed)
                    .metadata(doc.metadata)
                    .build()
            }
        }.getOrElse { e ->
            // 코루틴 취소 예외는 상위로 전파해야 함 (구조적 동시성)
            if (e is CancellationException) throw e
            logger.warn(
                "청크 압축 실패 — 원본 fallback. query={}, source={}, error={}",
                query, doc.metadata["source"], e.message,
            )
            doc
        }
    }

    private fun callCompressor(query: String, chunkText: String): String {
        val systemMessage = promptResource.getContentAsString(Charsets.UTF_8)
            .replace("{query}", query)
        return chatClient.prompt()
            .system(systemMessage)
            .user(chunkText)
            .options(OpenAiChatOptions.builder().model(properties.model).build())
            .call()
            .content()
            ?.trim()
            ?: ""
    }
}
