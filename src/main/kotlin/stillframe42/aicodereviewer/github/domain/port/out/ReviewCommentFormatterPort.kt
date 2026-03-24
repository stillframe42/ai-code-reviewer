package stillframe42.aicodereviewer.github.domain.port.out

import stillframe42.aicodereviewer.review.domain.model.CodeReview

// 코드 리뷰 결과를 PR 코멘트 문자열로 변환하는 아웃바운드 포트
interface ReviewCommentFormatterPort {
    fun format(review: CodeReview): String
}
