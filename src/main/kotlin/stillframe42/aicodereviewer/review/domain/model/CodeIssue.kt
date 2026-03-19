package stillframe42.aicodereviewer.review.domain.model

// 코드에서 발견된 단일 이슈 — 순수 도메인 모델 (외부 라이브러리 어노테이션 없음)
data class CodeIssue(
    val id: String,
    val category: IssueCategory,
    val line: Int?,
    val severity: IssueSeverity,
    val description: String,
    val suggestion: String
)
