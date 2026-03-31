package stillframe42.aicodereviewer.review.adapter.`in`.web.dto

// API 요청에서 Tool Calling 활성화 여부를 제어하는 enum
// WITHOUT_TOOLS → ReviewMode.Simple (기본값, installationId 불필요)
// WITH_TOOLS    → ReviewMode.WithGitHubTools (installationId 필수)
enum class ReviewModeRequest {
    WITHOUT_TOOLS,
    WITH_TOOLS,
}
