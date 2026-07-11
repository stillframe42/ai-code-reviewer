package stillframe42.aicodereviewer.review.domain.model

// PR 리뷰 완료 이벤트 — 성공(DONE) 또는 실패(FAILED) 시 발행.
// 발행 시점은 리뷰 파이프라인 종료(GitHub 코멘트 등록 전) — durationNanos 도 파이프라인 시간만 측정한다
data class ReviewCompletedEvent(
    val repo: String,
    val status: String,           // "DONE" | "FAILED"
    val issues: List<CodeIssue>,  // 실패 시 빈 리스트
    val durationNanos: Long,
)
