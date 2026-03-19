package stillframe42.aicodereviewer.review

import stillframe42.aicodereviewer.chat.AiProvider
import stillframe42.aicodereviewer.review.dto.CodeReviewResult

interface ReviewService {
    // 코드를 분석하여 구조화된 리뷰 결과를 반환 (Structured Output)
    suspend fun reviewCode(code: String, provider: AiProvider): CodeReviewResult
}
