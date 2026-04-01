package stillframe42.aicodereviewer.review.comparison

import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewModeRequest
import stillframe42.aicodereviewer.review.domain.model.CodeReview

// 한 가지 모드의 리뷰 실행 결과 + 측정 지표
data class ComparisonResult(
    val mode: ReviewModeRequest,
    val review: CodeReview,
    val latencyMs: Long,
    val estimatedOutputTokens: Int,
)
