package stillframe42.aicodereviewer.github.adapter.out.github.dto

// GET /repos/{owner}/{repo}/pulls/{number} 응답에서 필요한 필드만 추출
data class PrDescriptionResponse(
    val title: String,
    val body: String?,
)
