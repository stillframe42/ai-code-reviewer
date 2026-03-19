package stillframe42.aicodereviewer.review.adapter.out.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    @param:Value("classpath:prompts/review-system.st")
    private val systemPromptResource: Resource,

    @param:Value("classpath:prompts/review-user.st")
    private val userPromptResource: Resource,
) : AiReviewPort {

    // .entity()로 BeanOutputConverter가 JSON Schema를 프롬프트에 append하고 응답을 역직렬화
    override suspend fun reviewCode(code: String, provider: AiProvider): CodeReview =
        withContext(Dispatchers.IO) {
            promptBuilder.build(systemPromptResource, userPromptResource, mapOf("code" to code), provider)
                .call()
                .entity(CodeReviewAiResponse::class.java)
                ?.toDomain()
                ?: throw IllegalStateException("AI로부터 리뷰 결과를 받지 못했습니다")
        }
}
