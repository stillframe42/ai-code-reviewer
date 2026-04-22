package stillframe42.aicodereviewer.review.adapter.out.ai

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.AiPromptBuilder
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.advisor.CostTrackingAdvisor
import stillframe42.aicodereviewer.common.advisor.LoggingAdvisor
import stillframe42.aicodereviewer.common.advisor.RetryAdvisor
import java.util.UUID
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder
import stillframe42.aicodereviewer.common.langfuse.ReviewObservationContextHolder
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.adapter.out.ai.dto.CodeReviewAiResponse
import stillframe42.aicodereviewer.review.adapter.out.ai.tool.GitHubTools
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.ReviewContext
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// Spring AI 기반 코드 리뷰 출력 어댑터 — AiReviewPort 구현체
@Component
class SpringAiReviewAdapter(
    private val promptBuilder: AiPromptBuilder,
    private val gitHubTools: GitHubTools,
    private val loggingAdvisor: LoggingAdvisor,
    private val retryAdvisor: RetryAdvisor,
    private val costTrackingAdvisor: CostTrackingAdvisor,

    @param:Value("\${app.prompt.review-system}")
    private val systemPromptResource: Resource,

    @param:Value("classpath:prompts/review/review-user.st")
    private val userPromptResource: Resource,
) : AiReviewPort, Logging {

    // LLM이 Kotlin/Shell 코드의 $ 앞에 \를 붙이는 경우가 있어 \$ → $ 전처리 허용
    // Kotlin data class 역직렬화를 위해 KotlinModule 등록 필수
    private val lenientMapper = ObjectMapper().apply {
        registerKotlinModule()
        configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
        // AI가 알 수 없는 필드(title, suggestions 등)를 포함하는 경우 무시
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val converter = BeanOutputConverter(CodeReviewAiResponse::class.java, lenientMapper)

    // toolCallCounter: 요청별 독립 생성 — 병렬 리뷰 시 파일별로 카운터가 분리됨
    override suspend fun reviewCode(
        code: String,
        provider: AiProvider,
        mode: ReviewMode,
        reviewContext: ReviewContext?,
        modelName: String?,
        conventionContext: String?,
    ): CodeReview {
        val timeout = when (mode) {
            is ReviewMode.Simple -> SIMPLE_TIMEOUT
            is ReviewMode.WithGitHubTools -> TOOL_TIMEOUT
        }
        val toolCallCounter = AtomicInteger(0)
        // 상위에서 이미 설정된 traceId가 있으면 재사용한다 (review.root span 이 생성한 trace).
        // 없으면 새로 생성하여 LangfuseObservationHandler.onStart()에서 재사용하고,
        // Tool 실행(executeToolCall)에서 LangfuseTraceContextHolder.get()으로 안전하게 접근할 수 있다.
        val traceId = LangfuseTraceContextHolder.get() ?: UUID.randomUUID().toString()
        return withTimeout(timeout) {
            withContext(
                Dispatchers.IO +
                    ReviewObservationContextHolder.asElement(reviewContext) +
                    LangfuseTraceContextHolder.asElement(traceId),
            ) {
                val rawText = buildRequestSpec(code, provider, mode, toolCallCounter, modelName, conventionContext)
                    .call()
                    .content()
                    ?: throw IllegalStateException("AI로부터 빈 응답을 받았습니다")
                // AI가 preamble 텍스트나 마크다운 코드 펜스를 포함하는 경우 대비
                val jsonText = extractJson(rawText)
                converter.convert(jsonText)
                    .toDomain()
                    .copy(toolCallCount = toolCallCounter.get())
            }
        }
    }

    // AI 응답에서 JSON을 추출한다. 순수 JSON / 마크다운 코드 펜스 / preamble+JSON 세 가지 형식 처리.
    private fun extractJson(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return trimmed

        // ```json ... ``` 또는 ``` ... ``` 코드 펜스에서 추출
        val fenceMatch = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*\\n?```").find(trimmed)
        if (fenceMatch != null) return fenceMatch.groupValues[1].trim()

        // preamble 텍스트 이후 { ... } 추출
        val startIdx = trimmed.indexOf('{')
        val endIdx = trimmed.lastIndexOf('}')
        if (startIdx != -1 && endIdx > startIdx) return trimmed.substring(startIdx, endIdx + 1)

        return text
    }

    // ReviewMode에 따라 Tool 등록 + 카운터 전달 여부를 결정하여 ChatClient 요청 스펙을 구성한다
    private fun buildRequestSpec(
        code: String,
        provider: AiProvider,
        mode: ReviewMode,
        toolCallCounter: AtomicInteger,
        modelName: String?,
        conventionContext: String?,
    ): ChatClient.ChatClientRequestSpec =
        promptBuilder.build(
            systemPromptResource,
            userPromptResource,
            buildVariables(code, conventionContext),
            provider,
        )
            .advisors(loggingAdvisor, retryAdvisor, costTrackingAdvisor)
            .let { spec ->
                // modelName이 지정된 경우 ChatClient 기본 모델을 오버라이드
                if (modelName != null)
                    spec.options(AnthropicChatOptions.builder().model(modelName).build())
                else spec
            }
            .let { baseSpec ->
                when (mode) {
                    is ReviewMode.Simple -> baseSpec
                    is ReviewMode.WithGitHubTools -> {
                        logger.info("Tool Calling 활성화: installationId={}", mode.installationId)
                        baseSpec
                            .tools(gitHubTools)
                            .toolContext(mapOf(
                                "installationId" to mode.installationId,
                                "toolCallCounter" to toolCallCounter,
                            ))
                    }
                }
            }

    // convention_section: 컨텍스트가 있으면 위반 검출 지시문을 포함한 블록, 없으면 빈 문자열
    // format: BeanOutputConverter가 CodeReviewAiResponse 스키마로부터 생성한 JSON 포맷 명세
    private fun buildVariables(code: String, conventionContext: String?): Map<String, Any> {
        val conventionSection = if (!conventionContext.isNullOrBlank()) {
            """[참고 컨벤션 문서]
$conventionContext

위 컨벤션 문서의 "리뷰 체크리스트" 항목을 기준으로 diff에서 위반 사항을 확인합니다.
위반이 발견되면 severity MAJOR 이슈로 지적하고, 다른 이슈보다 먼저 나열합니다.
"""
        } else ""
        return mapOf(
            "code" to code,
            "convention_section" to conventionSection,
            "format" to converter.getFormat(),
        )
    }

    companion object {
        // ReviewMode에 따라 전체 타임아웃을 분기한다
        // Simple: 60s (AI 추론만)
        // WithGitHubTools: 180s (Tool 호출 5회×10s + AI 추론 여유)
        private val SIMPLE_TIMEOUT: Duration = 60.seconds
        private val TOOL_TIMEOUT: Duration = 180.seconds
    }
}
