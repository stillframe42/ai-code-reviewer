package stillframe42.aicodereviewer.common.metrics.event

import stillframe42.aicodereviewer.review.domain.model.CodeIssue

// PR 리뷰 완료 이벤트 — 성공(DONE) 또는 실패(FAILED) 시 발행
data class ReviewCompletedEvent(
    val repo: String,
    val status: String,           // "DONE" | "FAILED"
    val issues: List<CodeIssue>,  // 실패 시 빈 리스트
    val durationNanos: Long,
)
