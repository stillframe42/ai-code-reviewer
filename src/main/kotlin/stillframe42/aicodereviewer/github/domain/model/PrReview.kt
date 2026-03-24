package stillframe42.aicodereviewer.github.domain.model

// PR Reviews API에 등록할 리뷰 요청을 나타내는 도메인 모델
data class PrReview(
    val body: String,
    val event: PrReviewEvent = PrReviewEvent.COMMENT,
    val lineComments: List<PrReviewLineComment> = emptyList(),  // Phase 2에서 채워짐
)

// GitHub PR Review 이벤트 타입
enum class PrReviewEvent { COMMENT, APPROVE, REQUEST_CHANGES }

// 인라인 라인 코멘트 도메인 모델 — Phase 2에서 CodeIssue로부터 매핑된다
data class PrReviewLineComment(
    val path: String,
    val position: Int,  // diff position (1-based, hunk 내 순서)
    val body: String,
)
