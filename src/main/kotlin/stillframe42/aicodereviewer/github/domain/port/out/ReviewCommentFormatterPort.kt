package stillframe42.aicodereviewer.github.domain.port.out

import stillframe42.aicodereviewer.review.domain.model.CodeReview

// 코드 리뷰 결과를 PR 코멘트 문자열로 변환하는 아웃바운드 포트
interface ReviewCommentFormatterPort {
    fun format(review: CodeReview): String

    // 총 이슈 수(인라인 + 요약)를 명시적으로 전달하는 오버로드 — 기본 구현은 format(review)에 위임
    fun format(review: CodeReview, totalIssueCount: Int, lineCommentCount: Int): String = format(review)
}
