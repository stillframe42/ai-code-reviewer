package stillframe42.aicodereviewer.review.adapter.out.ai

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
import stillframe42.aicodereviewer.common.concurrent.BlockingTimeout
import stillframe42.aicodereviewer.common.exception.AiResponseException
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder
import stillframe42.aicodereviewer.common.langfuse.ObservationSessionContext
import stillframe42.aicodereviewer.common.langfuse.ObservationSessionContextHolder
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.adapter.out.ai.dto.CodeReviewAiResponse
import stillframe42.aicodereviewer.review.adapter.out.ai.tool.GitHubTools
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.ReviewContext
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort
import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

// Spring AI 기반 코드 리뷰 출력 어댑터 — AiReviewPort 구현체
@Component
class ReviewAdapter(
    private val promptBuilder: AiPromptBuilder,
    private val gitHubTools: GitHubTools,
    private val loggingAdvisor: LoggingAdvisor,
    private val costTrackingAdvisor: CostTrackingAdvisor,

    @param:Value("\${app.prompt.review-system}")
    private val systemPromptResource: Resource,

    @param:Value("classpath:prompts/review/review-user.st")
    private val userPromptResource: Resource,
) : AiReviewPort, Logging {

    // toolCallCounter: 요청별 독립 생성 — 병렬 리뷰 시 파일별로 카운터가 분리됨
    override fun reviewCode(
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
        // 상위(review.root)에서 이미 설정된 traceId가 있으면 재사용, 없으면 새로 생성 —
        // LangfuseObservationHandler.onStart()와 Tool 실행(executeToolCall)이 같은 값을 읽는다
        val previousTraceId = LangfuseTraceContextHolder.get()
        val traceId = previousTraceId ?: UUID.randomUUID().toString()
        val previousSession = ObservationSessionContextHolder.local.get()
        LangfuseTraceContextHolder.set(traceId)
        reviewContext?.let { ObservationSessionContextHolder.local.set(it.toObservationSessionContext()) }
        try {
            return BlockingTimeout.run(timeout) {
                val rawText = buildRequestSpec(code, provider, mode, toolCallCounter, modelName, conventionContext)
                    .call()
                    .content()
                    ?: throw AiResponseException("AI로부터 빈 응답을 받았습니다")
                // AI가 preamble 텍스트나 마크다운 코드 펜스를 포함하는 경우 대비
                val jsonText = extractJson(rawText)
                reviewResponseConverter.convert(jsonText)
                    .toDomain()
                    .copy(toolCallCount = toolCallCounter.get())
            }
        } finally {
            if (previousTraceId != null) LangfuseTraceContextHolder.set(previousTraceId) else LangfuseTraceContextHolder.clear()
            if (previousSession != null) ObservationSessionContextHolder.local.set(previousSession) else ObservationSessionContextHolder.local.remove()
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
            .advisors(loggingAdvisor, costTrackingAdvisor)
            .let { spec ->
                // modelName이 지정된 경우 ChatClient 기본 모델을 오버라이드
                if (modelName != null)
                    spec.options(AnthropicChatOptions.builder().model(modelName))
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
            "format" to reviewResponseConverter.getFormat(),
        )
    }

    // Langfuse metadata 키 계약 — LangfuseObservationHandlerTest 가 이 키/값 형식을 검증한다
    private fun ReviewContext.toObservationSessionContext() = ObservationSessionContext(
        sessionId = reviewRequestId.toString(),
        metadata = mapOf(
            "review.request.id" to reviewRequestId.toString(),
            "review.pr.number" to prNumber.toString(),
            "review.repo" to repoFullName,
        ),
    )

    companion object {
        // ReviewMode에 따라 전체 타임아웃을 분기한다
        // Simple: 60s (AI 추론만)
        // WithGitHubTools: 180s (Tool 호출 5회×10s + AI 추론 여유)
        private val SIMPLE_TIMEOUT: Duration = 60.seconds
        private val TOOL_TIMEOUT: Duration = 180.seconds
    }
}

// LLM이 Kotlin/Shell 코드의 $ 앞에 \를 붙이는 경우가 있어 \$ → $ 전처리 허용
// Kotlin data class 역직렬화를 위해 KotlinModule 등록 필수
// AI가 알 수 없는 필드(title, suggestions 등)를 포함하는 경우 무시 (Jackson 3 기본값이지만 계약으로 명시)
// 파일 레벨 internal — CodeReviewAiResponseParsingTest 가 운영과 동일한 인스턴스로 lenient 계약을 검증한다
internal val reviewResponseConverter: BeanOutputConverter<CodeReviewAiResponse> = BeanOutputConverter(
    CodeReviewAiResponse::class.java,
    JsonMapper.builder()
        .addModule(kotlinModule())
        .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build(),
)
