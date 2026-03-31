package stillframe42.aicodereviewer.review.adapter.out.ai

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.AiPromptBuilder
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.adapter.out.ai.dto.CodeReviewAiResponse
import stillframe42.aicodereviewer.review.adapter.out.ai.tool.GitHubTools
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// Spring AI 기반 코드 리뷰 출력 어댑터 — AiReviewPort 구현체
@Component
class SpringAiReviewAdapter(
    private val promptBuilder: AiPromptBuilder,
    private val gitHubTools: GitHubTools,

    @param:Value("\${app.prompt.review-system}")
    private val systemPromptResource: Resource,

    @param:Value("classpath:prompts/review-user.st")
    private val userPromptResource: Resource,
) : AiReviewPort, Logging {

    // LLM이 Kotlin/Shell 코드의 $ 앞에 \를 붙이는 경우가 있어 \$ → $ 전처리 허용
    // Kotlin data class 역직렬화를 위해 KotlinModule 등록 필수
    private val lenientMapper = ObjectMapper().apply {
        registerKotlinModule()
        configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
    }
    private val converter = BeanOutputConverter(CodeReviewAiResponse::class.java, lenientMapper)

    // toolCallCounter: 요청별 독립 생성 — 병렬 리뷰 시 파일별로 카운터가 분리됨
    override suspend fun reviewCode(code: String, provider: AiProvider, mode: ReviewMode): CodeReview {
        val toolCallCounter = AtomicInteger(0)
        return executeWithRetry(mode) {
            buildRequestSpec(code, provider, mode, toolCallCounter)
                .call()
                .entity(converter)
                ?.toDomain()
                ?.copy(toolCallCount = toolCallCounter.get())
                ?: throw IllegalStateException("AI로부터 리뷰 결과를 받지 못했습니다")
        }
    }

    // ReviewMode에 따라 Tool 등록 + 카운터 전달 여부를 결정하여 ChatClient 요청 스펙을 구성한다
    private fun buildRequestSpec(
        code: String,
        provider: AiProvider,
        mode: ReviewMode,
        toolCallCounter: AtomicInteger,
    ) = promptBuilder.build(systemPromptResource, userPromptResource, mapOf("code" to code), provider)
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

    // ReviewMode에 따라 전체 타임아웃을 분기한다
    // Simple: 60s (AI 추론만)
    // WithGitHubTools: 180s (Tool 호출 5회×10s + AI 추론 여유)
    private suspend fun <T> executeWithRetry(mode: ReviewMode, block: suspend () -> T): T {
        val timeout = when (mode) {
            is ReviewMode.Simple -> SIMPLE_TIMEOUT
            is ReviewMode.WithGitHubTools -> TOOL_TIMEOUT
        }
        var lastEx: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return withTimeout(timeout) {
                    withContext(Dispatchers.IO) { block() }
                }
            } catch (e: TimeoutCancellationException) {
                // 시도별 타임아웃 — 외부 코루틴 취소와 구분하기 위해 별도 처리
                lastEx = e
                logger.warn("AI 리뷰 타임아웃 (시도 {}/{}): {} 초과", attempt, MAX_ATTEMPTS, timeout)
            } catch (e: Exception) {
                lastEx = e
                logger.warn("AI 리뷰 실패 (시도 {}/{}): {}", attempt, MAX_ATTEMPTS, e.message)
            }
            // 재시도 전 대기 — Rate Limit(429) 등 일시적 오류 회피
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY)
        }
        throw lastEx ?: error("재시도 횟수(${MAX_ATTEMPTS}회) 초과 후 예외가 없음")
    }

    companion object {
        private val SIMPLE_TIMEOUT: Duration = 60.seconds   // Simple 모드: AI 추론만
        private val TOOL_TIMEOUT: Duration = 180.seconds    // Tool Calling 모드: Tool 5회×10s + AI 추론 여유
        private const val MAX_ATTEMPTS = 2
        private val RETRY_DELAY: Duration = 5.seconds       // 재시도 전 대기 — Rate Limit(429) 등 일시적 오류 회피
    }
}
