package stillframe42.aicodereviewer.review.adapter.out.ai

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.ai.converter.BeanOutputConverter
import stillframe42.aicodereviewer.common.Logging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.AiPromptBuilder
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.adapter.out.ai.dto.CodeReviewAiResponse
import stillframe42.aicodereviewer.review.adapter.out.ai.tool.GitHubTools
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort
import kotlin.time.Duration.Companion.milliseconds

// Spring AI 기반 코드 리뷰 출력 어댑터 — AiReviewPort 구현체
@Component
class SpringAiReviewAdapter(
    private val promptBuilder: AiPromptBuilder,
    private val gitHubFileTools: GitHubTools,

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

    // .entity(converter)로 JSON Schema를 프롬프트에 append하고 lenient ObjectMapper로 역직렬화
    override suspend fun reviewCode(code: String, provider: AiProvider, mode: ReviewMode): CodeReview =
        executeWithRetry {
            buildRequestSpec(code, provider, mode)
                .call()
                .entity(converter)
                ?.toDomain()
                ?: throw IllegalStateException("AI로부터 리뷰 결과를 받지 못했습니다")
        }

    // ReviewMode에 따라 Tool 등록 여부를 결정하여 ChatClient 요청 스펙을 구성한다
    private fun buildRequestSpec(code: String, provider: AiProvider, mode: ReviewMode) =
        promptBuilder.build(systemPromptResource, userPromptResource, mapOf("code" to code), provider)
            .let { baseSpec ->
                when (mode) {
                    is ReviewMode.Simple -> baseSpec
                    is ReviewMode.WithGitHubTools -> {
                        logger.info("Tool Calling 활성화: installationId={}", mode.installationId)
                        baseSpec
                            .tools(gitHubFileTools)
                            .toolContext(mapOf("installationId" to mode.installationId))
                    }
                }
            }

    // withTimeout: 시도별로 적용 — 1차 타임아웃이 2차 시도 시간을 잠식하지 않도록 분리
    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastEx: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return withTimeout(TIMEOUT_MS.milliseconds) {
                    withContext(Dispatchers.IO) { block() }
                }
            } catch (e: TimeoutCancellationException) {
                // 시도별 타임아웃 — 외부 코루틴 취소와 구분하기 위해 별도 처리
                lastEx = e
                logger.warn("AI 리뷰 타임아웃 (시도 {}/{}): {}ms 초과", attempt, MAX_ATTEMPTS, TIMEOUT_MS)
            } catch (e: Exception) {
                lastEx = e
                logger.warn("AI 리뷰 실패 (시도 {}/{}): {}", attempt, MAX_ATTEMPTS, e.message)
            }
            // 재시도 전 대기 — Rate Limit(429) 등 일시적 오류 회피
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS.milliseconds)
        }
        throw lastEx ?: error("재시도 횟수(${MAX_ATTEMPTS}회) 초과 후 예외가 없음")
    }

    companion object {
        private const val TIMEOUT_MS = 60_000L  // 시도별 타임아웃 — 파일당 AI 응답에 충분한 여유 확보
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 5_000L  // 재시도 전 대기 — Rate Limit(429) 등 일시적 오류 회피
    }
}
