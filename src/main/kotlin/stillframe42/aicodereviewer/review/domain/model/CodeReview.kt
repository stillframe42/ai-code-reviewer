package stillframe42.aicodereviewer.review.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

// 코드 리뷰 전체 결과
data class CodeReview(
    @field:JsonProperty("overall_score") val overallScore: Int,
    val summary: String,
    val issues: List<CodeIssue>,
    val positives: List<String>
)
