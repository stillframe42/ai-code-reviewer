package stillframe42.aicodereviewer.evaluation.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.EvaluationProperties
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationMetric
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationScore
import stillframe42.aicodereviewer.evaluation.domain.port.out.RagEvaluationPort
import stillframe42.aicodereviewer.rag.domain.model.RagDocument

@Component
class EvaluationAdapter(
    @param:Qualifier("openAiChatClient") private val chatClient: ChatClient,
    private val evaluationProperties: EvaluationProperties,
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader,
    @param:Value("classpath:prompts/evaluation/evaluation-context-precision.st")
    private val contextPrecisionPrompt: Resource,
    @param:Value("classpath:prompts/evaluation/evaluation-context-recall.st")
    private val contextRecallPrompt: Resource,
    @param:Value("classpath:prompts/evaluation/evaluation-answer-relevancy.st")
    private val answerRelevancyPrompt: Resource,
) : RagEvaluationPort, Logging {

    override suspend fun evaluateFaithfulness(
        context: List<RagDocument>,
        generatedReview: String,
    ): EvaluationScore {
        val contextText = context.joinToString("\n\n---\n\n") { it.text }
        val promptResource = resourceLoader.getResource(evaluationProperties.faithfulnessPrompt)
        val prompt = promptResource.getContentAsString(Charsets.UTF_8)
            .replace("{context}", contextText)
            .replace("{review}", generatedReview)

        val raw = callLlm(prompt)
        return parseScore(raw, EvaluationMetric.FAITHFULNESS)
    }

    override suspend fun evaluateContextPrecision(
        query: String,
        retrievedDocs: List<RagDocument>,
        relevantConvention: String,
    ): EvaluationScore {
        val documentsText = retrievedDocs.mapIndexed { index, doc ->
            "[문서 ${index + 1}] (ID: ${doc.id})\n${doc.text}"
        }.joinToString("\n\n---\n\n")

        val prompt = contextPrecisionPrompt.getContentAsString(Charsets.UTF_8)
            .replace("{query}", query)
            .replace("{relevant_convention}", relevantConvention)
            .replace("{documents}", documentsText)

        val raw = callLlm(prompt)
        return parseScore(raw, EvaluationMetric.CONTEXT_PRECISION)
    }

    override suspend fun evaluateContextRecall(
        expectedIssues: List<String>,
        retrievedDocs: List<RagDocument>,
    ): EvaluationScore {
        val issuesText = expectedIssues.mapIndexed { index, issue ->
            "${index + 1}. $issue"
        }.joinToString("\n")

        val documentsText = retrievedDocs.mapIndexed { index, doc ->
            "[문서 ${index + 1}] (ID: ${doc.id})\n${doc.text}"
        }.joinToString("\n\n---\n\n")

        val prompt = contextRecallPrompt.getContentAsString(Charsets.UTF_8)
            .replace("{expected_issues}", issuesText)
            .replace("{documents}", documentsText)

        val raw = callLlm(prompt)
        return parseScore(raw, EvaluationMetric.CONTEXT_RECALL)
    }

    override suspend fun evaluateAnswerRelevancy(
        inputCode: String,
        expectedIssues: List<String>,
        generatedReview: String,
    ): EvaluationScore {
        val issuesText = expectedIssues.mapIndexed { index, issue ->
            "${index + 1}. $issue"
        }.joinToString("\n")

        val prompt = answerRelevancyPrompt.getContentAsString(Charsets.UTF_8)
            .replace("{input_code}", inputCode)
            .replace("{expected_issues}", issuesText)
            .replace("{review}", generatedReview)

        val raw = callLlm(prompt)
        return parseScore(raw, EvaluationMetric.ANSWER_RELEVANCY)
    }

    private suspend fun callLlm(systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            chatClient.prompt()
                .system(systemPrompt)
                .user("위 지침에 따라 평가하고 JSON으로 응답하세요.")
                .options(
                    OpenAiChatOptions.builder()
                        .model(evaluationProperties.model)
                        .temperature(0.0),
                )
                .call()
                .content()
                ?.trim()
                ?: ""
        }

    // JSON 응답 파싱 — 마크다운 코드 펜스 래핑 + 앞뒤 비JSON 텍스트 제거
    private fun parseScore(raw: String, metric: EvaluationMetric): EvaluationScore {
        val jsonStr = extractJson(raw)

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

    companion object {
        // LLM 응답에서 JSON 객체를 추출 — 코드 펜스/앞뒤 텍스트 제거 + bracket counter로
        // 한국어 문장 안의 중괄호도 안전하게 처리한다.
        // 인스턴스 상태에 의존하지 않으므로 companion object로 노출 (단위 테스트 용이).
        internal fun extractJson(raw: String): String {
            val stripped = raw.replace(Regex("```(json)?\\s*"), "").trim()
            val start = stripped.indexOf('{')
            if (start < 0) return stripped
            var depth = 0
            var inString = false
            var escape = false
            for (i in start until stripped.length) {
                val c = stripped[i]
                when {
                    escape -> escape = false
                    c == '\\' && inString -> escape = true
                    c == '"' -> inString = !inString
                    !inString && c == '{' -> depth++
                    !inString && c == '}' -> {
                        depth--
                        if (depth == 0) return stripped.substring(start, i + 1)
                    }
                }
            }
            return stripped.substring(start)
        }
    }
}
