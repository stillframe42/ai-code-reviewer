package stillframe42.aicodereviewer.review.adapter.out.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.AiPromptBuilder
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.adapter.out.ai.dto.CodeReviewAiResponse
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// Spring AI 기반 코드 리뷰 출력 어댑터 — AiReviewPort 구현체
@Component
class SpringAiReviewAdapter(
    private val promptBuilder: AiPromptBuilder,

    @param:Value("\${app.prompt.review-system}")
    private val systemPromptResource: Resource,

    @param:Value("classpath:prompts/review-user.st")
    private val userPromptResource: Resource,
) : AiReviewPort {

    // .entity()로 BeanOutputConverter가 JSON Schema를 프롬프트에 append하고 응답을 역직렬화
    // withTimeout: 총 30초 초과 시 TimeoutCancellationException (재시도 포함한 전체 한도)
    // 재시도: 일반 오류에 한해 1회 재시도 (타임아웃은 즉시 포기)
    override suspend fun reviewCode(code: String, provider: AiProvider): CodeReview =
        withTimeout(TIMEOUT_MS) {
            var lastEx: Exception? = null
            for (attempt in 1..MAX_ATTEMPTS) {
                try {
                    return@withTimeout withContext(Dispatchers.IO) {
                        promptBuilder.build(systemPromptResource, userPromptResource, mapOf("code" to code), provider)
                            .call()
                            .entity(CodeReviewAiResponse::class.java)
                            ?.toDomain()
                            ?: throw IllegalStateException("AI로부터 리뷰 결과를 받지 못했습니다")
                    }
                } catch (e: Exception) {
                    lastEx = e
                    logger.warn("AI 리뷰 실패 (시도 {}/{}): {}", attempt, MAX_ATTEMPTS, e.message)
                }
            }
            throw lastEx!!
        }

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val MAX_ATTEMPTS = 2
        private val logger = LoggerFactory.getLogger(SpringAiReviewAdapter::class.java)
    }
}
