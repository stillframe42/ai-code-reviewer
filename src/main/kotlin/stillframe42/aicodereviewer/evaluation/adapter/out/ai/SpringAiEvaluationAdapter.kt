package stillframe42.aicodereviewer.evaluation.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.EvaluationProperties
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationMetric
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationScore
import stillframe42.aicodereviewer.evaluation.domain.port.out.RagEvaluationPort

@Component
class SpringAiEvaluationAdapter(
    @param:Qualifier("openAiChatClient") private val chatClient: ChatClient,
    private val evaluationProperties: EvaluationProperties,
    private val objectMapper: ObjectMapper,
    @param:Value("classpath:prompts/evaluation-faithfulness.st")
    private val faithfulnessPrompt: Resource,
    @param:Value("classpath:prompts/evaluation-context-precision.st")
    private val contextPrecisionPrompt: Resource,
    @param:Value("classpath:prompts/evaluation-context-recall.st")
    private val contextRecallPrompt: Resource,
) : RagEvaluationPort, Logging {

    override suspend fun evaluateFaithfulness(
        context: List<Document>,
        generatedReview: String,
    ): EvaluationScore {
        val contextText = context.joinToString("\n\n---\n\n") { it.text ?: "" }
        val prompt = faithfulnessPrompt.getContentAsString(Charsets.UTF_8)
            .replace("{context}", contextText)
            .replace("{review}", generatedReview)

        val raw = callLlm(prompt)
        return parseScore(raw, EvaluationMetric.FAITHFULNESS)
    }

    override suspend fun evaluateContextPrecision(
        query: String,
        retrievedDocs: List<Document>,
        relevantConvention: String,
    ): EvaluationScore {
        // Task 3에서 구현
        TODO("Task 3에서 구현")
    }

    override suspend fun evaluateContextRecall(
        expectedIssues: List<String>,
        retrievedDocs: List<Document>,
    ): EvaluationScore {
        // Task 3에서 구현
        TODO("Task 3에서 구현")
    }

    private suspend fun callLlm(systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            chatClient.prompt()
                .system(systemPrompt)
                .user("위 지침에 따라 평가하고 JSON으로 응답하세요.")
                .options(
                    OpenAiChatOptions.builder()
                        .model(evaluationProperties.model)
                        .temperature(0.0)
                        .build(),
                )
                .call()
                .content()
                ?.trim()
                ?: ""
        }

    // JSON 응답 파싱 — 마크다운 코드 펜스 래핑도 처리
    private fun parseScore(raw: String, metric: EvaluationMetric): EvaluationScore {
        val jsonStr = raw
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        return runCatching {
            val map: Map<String, Any> = objectMapper.readValue(jsonStr)
            val score = (map["score"] as Number).toDouble().coerceIn(0.0, 1.0)
            val reason = map["reason"]?.toString() ?: ""
            @Suppress("UNCHECKED_CAST")
            val details = (map["details"] as? Map<String, Any>) ?: emptyMap()
            EvaluationScore(metric = metric, score = score, reason = reason, details = details)
        }.getOrElse {
            logger.warn("평가 응답 파싱 실패: metric={}, raw={}", metric, raw)
            EvaluationScore(
                metric = metric,
                score = 0.0,
                reason = "파싱 실패: $raw",
            )
        }
    }
}
