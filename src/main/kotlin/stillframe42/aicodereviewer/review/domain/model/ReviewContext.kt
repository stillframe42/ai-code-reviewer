package stillframe42.aicodereviewer.review.domain.model

// 리뷰 요청의 컨텍스트 정보 — LLM 호출 추적 시 메타 정보로 활용
data class ReviewContext(
    val reviewRequestId: Long,
    val prNumber: Int,
    val repoFullName: String,
)
