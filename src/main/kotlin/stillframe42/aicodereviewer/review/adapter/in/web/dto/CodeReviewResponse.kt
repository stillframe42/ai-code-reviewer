package stillframe42.aicodereviewer.review.adapter.`in`.web.dto

import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview

// ReviewController 응답 DTO — 도메인 모델(CodeReview)과 JSON 직렬화 관심사를 분리한다.
// 필드명을 snake_case로 선언하여 @JsonProperty 없이 API 포맷을 유지한다.
data class CodeReviewResponse(
    val overall_score: Int,
    val summary: String,
    val issues: List<CodeIssue>,
    val positives: List<String>,
    val tool_call_count: Int,
) {
    companion object {
        fun from(review: CodeReview) = CodeReviewResponse(
            overall_score = review.overallScore,
            summary = review.summary,
            issues = review.issues,
            positives = review.positives,
            tool_call_count = review.toolCallCount,
        )
    }
}
