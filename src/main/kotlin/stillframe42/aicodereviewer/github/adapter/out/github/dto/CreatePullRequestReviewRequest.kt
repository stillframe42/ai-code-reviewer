package stillframe42.aicodereviewer.github.adapter.out.github.dto

// POST /repos/{owner}/{repo}/pulls/{pull_number}/reviews 요청 DTO
data class CreatePullRequestReviewRequest(
    val body: String,
    val event: String,  // "COMMENT" | "APPROVE" | "REQUEST_CHANGES"
    val comments: List<ReviewLineComment> = emptyList(),
)

// 인라인 라인 코멘트 항목 DTO
data class ReviewLineComment(
    val path: String,
    val position: Int,
    val body: String,
)
