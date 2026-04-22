package stillframe42.aicodereviewer.github.domain.model

// PR Reviews API에 등록할 리뷰 요청을 나타내는 도메인 모델
data class PrReview(
    val body: String,
    val event: PrReviewEvent = PrReviewEvent.COMMENT,
    val lineComments: List<PrReviewLineComment> = emptyList(),
    // 리뷰를 앵커링할 커밋 SHA — 인라인 코멘트 포함 시 GitHub API 필수값
    val commitId: String? = null,
)

// GitHub PR Review 이벤트 타입
enum class PrReviewEvent { COMMENT, APPROVE, REQUEST_CHANGES }

// 인라인 라인 코멘트 도메인 모델 — CodeIssue로부터 매핑된다
data class PrReviewLineComment(
    val path: String,
    val position: Int,  // diff position (1-based, hunk 내 순서)
    val body: String,
)
