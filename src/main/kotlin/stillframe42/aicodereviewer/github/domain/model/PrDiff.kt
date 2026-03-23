package stillframe42.aicodereviewer.github.domain.model

// GitHub API에서 조회한 PR의 unified diff 원본
data class PrDiff(
    val content: String,  // unified diff 형식 원본 텍스트
)
