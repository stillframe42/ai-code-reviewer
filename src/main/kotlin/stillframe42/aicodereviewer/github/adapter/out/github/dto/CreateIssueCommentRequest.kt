package stillframe42.aicodereviewer.github.adapter.out.github.dto

// POST /repos/{owner}/{repo}/issues/{number}/comments 요청 DTO
// GitHub API 필드명이 "body"와 일치하므로 @JsonProperty 불필요
data class CreateIssueCommentRequest(
    val body: String,
)
