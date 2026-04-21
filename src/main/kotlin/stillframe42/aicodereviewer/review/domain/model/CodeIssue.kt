package stillframe42.aicodereviewer.review.domain.model

// 코드에서 발견된 단일 이슈 — 순수 도메인 모델 (외부 라이브러리 어노테이션 없음)
data class CodeIssue(
    val id: String,
    val category: IssueCategory,
    val filename: String? = null,  // 이슈가 발생한 파일 경로 (예: src/main/kotlin/Foo.kt). 특정 불가 시 null
    val line: Int?,
    val severity: IssueSeverity,
    val description: String,
    val suggestion: String,
    // CoT 프롬프트(v12)에서 issue 도출 reasoning을 명시적으로 채움. 다른 버전에서는 null.
    val reasoning: String? = null,
)
