package stillframe42.aicodereviewer.review

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.chat.AiProvider
import stillframe42.aicodereviewer.common.AiPromptBuilder
import stillframe42.aicodereviewer.review.dto.CodeReviewResult

@Service
class DefaultReviewService(
    private val promptBuilder: AiPromptBuilder,

    @param:Value("classpath:prompts/review-system.st")
    private val systemPromptResource: Resource,

    @param:Value("classpath:prompts/review-user.st")
    private val userPromptResource: Resource,
) : ReviewService {

    // .entity()로 BeanOutputConverter가 JSON Schema를 프롬프트에 append하고 응답을 역직렬화
    override suspend fun reviewCode(code: String, provider: AiProvider): CodeReviewResult =
        withContext(Dispatchers.IO) {
            promptBuilder.build(systemPromptResource, userPromptResource, mapOf("code" to code), provider)
                .call()
                .entity(CodeReviewResult::class.java)
                ?: throw IllegalStateException("AI로부터 리뷰 결과를 받지 못했습니다")
        }
}
