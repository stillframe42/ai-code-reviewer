package stillframe42.aicodereviewer.github.adapter.out.github.dto

// POST /repos/{owner}/{repo}/pulls/{number}/reviews 응답 DTO
// GitHub API는 생성된 리뷰의 id를 최상위 필드로 반환한다
data class PullRequestReviewResponse(
    val id: Long,
)
