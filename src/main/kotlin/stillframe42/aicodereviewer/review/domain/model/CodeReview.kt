package stillframe42.aicodereviewer.review.domain.model

// 코드 리뷰 전체 결과 — 순수 도메인 모델 (외부 라이브러리 어노테이션 없음)
data class CodeReview(
    val overallScore: Int,
    val summary: String,
    val issues: List<CodeIssue>,
    val positives: List<String>,
    val toolCallCount: Int = 0,
)
