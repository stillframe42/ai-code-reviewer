package stillframe42.aicodereviewer.github.domain.model

// AI 리뷰 결과를 GitHub PR에 등록할 코멘트
data class ReviewComment(
    val body: String,  // 마크다운 형식 코멘트 본문
)
