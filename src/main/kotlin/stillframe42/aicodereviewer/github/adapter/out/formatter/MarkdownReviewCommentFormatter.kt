package stillframe42.aicodereviewer.github.adapter.out.formatter

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.port.out.ReviewCommentFormatterPort
import stillframe42.aicodereviewer.review.domain.model.CodeReview

// CodeReview 결과를 GitHub PR 코멘트용 마크다운 문자열로 변환하는 어댑터
@Component
class MarkdownReviewCommentFormatter : ReviewCommentFormatterPort {

    override fun format(review: CodeReview): String = buildString {
        appendLine("## AI 코드 리뷰 결과")
        appendLine()
        appendLine("**종합 점수: ${review.overallScore}/10**")
        appendLine()
        appendLine("### 요약")
        appendLine(review.summary)
        appendLine()
        appendLine("---")
        appendLine()

        appendLine("### 이슈 목록 (${review.issues.size}건)")
        appendLine()
        if (review.issues.isEmpty()) {
            appendLine("발견된 이슈가 없습니다.")
        } else {
            review.issues.forEach { issue ->
                appendLine("#### [${issue.severity}] ${issue.category} — ${issue.description}")
                issue.line?.let { appendLine("- **라인**: ${it}번째 줄") }
                appendLine("- **제안**: ${issue.suggestion}")
                appendLine()
            }
        }

        appendLine("---")
        appendLine()

        // 항목이 없으면 섹션 전체 생략
        if (review.positives.isNotEmpty()) {
            appendLine("### 잘한 점")
            review.positives.forEach { positive ->
                appendLine("- $positive")
            }
            appendLine()
            appendLine("---")
            appendLine()
        }

        append("*이 리뷰는 AI가 자동으로 생성했습니다.*")
    }
}
