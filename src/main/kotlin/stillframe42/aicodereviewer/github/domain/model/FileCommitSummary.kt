package stillframe42.aicodereviewer.github.domain.model

// date 는 ISO-8601 문자열 그대로 유지 — 표시(take(10))에만 사용되어 파싱 불필요
data class FileCommitSummary(
    val sha: String,
    val message: String,
    val authorName: String,
    val date: String,
)
