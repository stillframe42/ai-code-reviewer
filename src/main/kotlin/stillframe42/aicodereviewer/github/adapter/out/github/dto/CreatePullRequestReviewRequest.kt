package stillframe42.aicodereviewer.github.adapter.out.github.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

// POST /repos/{owner}/{repo}/pulls/{pull_number}/reviews 요청 DTO
// commit_id: 인라인 코멘트 포함 시 GitHub API 필수값 — null이면 직렬화에서 제외
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreatePullRequestReviewRequest(
    val body: String,
    val event: String,  // "COMMENT" | "APPROVE" | "REQUEST_CHANGES"
    val comments: List<ReviewLineComment> = emptyList(),
    @field:JsonProperty("commit_id")
    val commitId: String? = null,
)

// 인라인 라인 코멘트 항목 DTO
data class ReviewLineComment(
    val path: String,
    val position: Int,
    val body: String,
)
