package stillframe42.aicodereviewer.review.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// 코드 리뷰 유스케이스 구현 — AI 포트에 위임하며, 향후 이력 저장·사용량 제한 등 비즈니스 로직이 추가되는 레이어
@Service
class ReviewService(private val aiReviewPort: AiReviewPort) : ReviewUseCase {

    override suspend fun reviewCode(code: String, provider: AiProvider): CodeReview =
        aiReviewPort.reviewCode(code, provider)
}
