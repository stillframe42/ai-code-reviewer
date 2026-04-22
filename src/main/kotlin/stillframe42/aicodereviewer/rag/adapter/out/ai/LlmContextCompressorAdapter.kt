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
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.config.RagCompressionProperties
import stillframe42.aicodereviewer.rag.domain.port.out.ContextCompressorPort

// gpt-4o-mini 기반 추출 압축 어댑터 — ContextCompressorPort 구현.
// 임계값 이하 청크는 LLM 호출 없이 우회. per-chunk fail-open: 한 청크 실패 시 해당 청크만 원본 통과.
@Component
class LlmContextCompressorAdapter(
    @param:Qualifier("openAiChatClient") private val chatClient: ChatClient,
    private val properties: RagCompressionProperties,
    @param:Value("classpath:prompts/context-compressor.st")
    private val promptResource: Resource,
    private val observabilityPort: ObservabilityPort,
) : ContextCompressorPort, Logging {

    // cl100k_base 토큰 카운터 — 압축 임계값 판정용
    private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator()

    override suspend fun compress(
        query: String,
        documents: List<Document>,
    ): List<Document> {
        val beforeTokens = documents.sumOf { tokenEstimator.estimate(it.text ?: "") }
        val handle = observabilityPort.startSpan(
            name = "rag.compress",
            input = mapOf("query" to query, "document_count" to documents.size, "before_tokens" to beforeTokens),
        )
        return try {
            val result = documents.mapNotNull { doc -> compressOne(query, doc) }
            val afterTokens = result.sumOf { tokenEstimator.estimate(it.text ?: "") }
            observabilityPort.endSpan(
                handle,
                output = mapOf(
                    "result_count" to result.size,
                    "after_tokens" to afterTokens,
                ),
                metadata = mapOf(
                    "before_tokens" to beforeTokens,
                    "after_tokens" to afterTokens,
                    "compression_ratio" to if (beforeTokens > 0) "%.2f".format(afterTokens.toDouble() / beforeTokens) else "N/A",
                ),
            )
            result
        } catch (e: Throwable) {
            observabilityPort.endSpanWithError(handle, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    // 단일 청크 압축 — 임계값 이하면 우회, 초과면 LLM 호출, 실패 시 원본 fallback, 빈/NONE 결과는 null
    private suspend fun compressOne(query: String, doc: Document): Document? {
        val text = doc.text ?: return null
        val tokens = tokenEstimator.estimate(text)

        // 작은 청크는 압축 우회 (단일 토픽으로 노이즈 적음)
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
        // temperature=0.0: borderline chunk에 대한 gpt-4o-mini의 relevance 판정이
        // run마다 달라지는 비결정성을 억제한다 (N=5 실험에서 608 bytes variance 재현됨).
        // 완전한 결정론은 아니지만 동일 chunk를 같은 판정으로 통과시킬 확률이 크게 높아진다.
        return chatClient.prompt()
            .system(systemMessage)
            .user(chunkText)
            .options(
                OpenAiChatOptions.builder()
                    .model(properties.model)
                    .temperature(0.0)
                    .build(),
            )
            .call()
            .content()
            ?.trim()
            ?: ""
    }
}
