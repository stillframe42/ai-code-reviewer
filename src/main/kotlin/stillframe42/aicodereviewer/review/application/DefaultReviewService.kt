package stillframe42.aicodereviewer.review.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort
import stillframe42.aicodereviewer.review.domain.service.DiffPreprocessor

// 코드 리뷰 유스케이스 구현 — AI 포트에 위임하며, 향후 이력 저장·사용량 제한 등 비즈니스 로직이 추가되는 레이어
@Service
class DefaultReviewService(
    private val aiReviewPort: AiReviewPort,
    private val diffPreprocessor: DiffPreprocessor,
) : ReviewUseCase {

    override suspend fun reviewCode(
        code: String,
        provider: AiProvider,
        diffOptions: DiffFilterOptions?,
    ): CodeReview {
        // diffOptions가 있을 때만 전처리 수행 — null이면 기존 동작 유지
        val processedCode = if (diffOptions != null) {
            diffPreprocessor.preprocess(code, diffOptions).diff
        } else {
            code
        }
        return aiReviewPort.reviewCode(processedCode, provider)
    }
}
