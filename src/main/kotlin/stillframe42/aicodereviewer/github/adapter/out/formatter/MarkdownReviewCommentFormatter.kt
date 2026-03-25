package stillframe42.aicodereviewer.github.adapter.out.formatter

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.port.out.ReviewCommentFormatterPort
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// CodeReview 결과를 GitHub PR 코멘트용 마크다운 문자열로 변환하는 어댑터
@Component
class MarkdownReviewCommentFormatter(
    @param:Value("\${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}")
    private val modelName: String,
) : ReviewCommentFormatterPort {

    override fun format(review: CodeReview): String = buildString {
        appendLine("## 🤖 AI Code Review")
        appendLine()

        appendLine("### 📋 Summary")
        appendLine()
        appendLine("> 종합 점수: ${review.overallScore}/10")
        appendLine()
        appendLine(review.summary)
        appendLine()

        appendLine("### 🚨 Issues Found (${review.issues.size})")
        appendLine()
        if (review.issues.isEmpty()) {
            appendLine("발견된 이슈가 없습니다.")
        } else {
            appendLine("| Severity | Type | Description |")
            appendLine("|----------|------|-------------|")
            review.issues.forEach { issue ->
                val lineTag = issue.line?.let { " (L$it)" } ?: ""
                val description = "${issue.description}$lineTag<br>💡 ${issue.suggestion}"
                appendLine("| ${severityEmoji(issue.severity)} ${issue.severity} | ${issue.category} | $description |")
            }
        }
        appendLine()

        // 항목이 없으면 섹션 전체 생략
        if (review.positives.isNotEmpty()) {
            appendLine("### ✅ Positives")
            appendLine()
            review.positives.forEach { positive ->
                appendLine("- $positive")
            }
            appendLine()
        }

        val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        append("> 생성 시간: $timestamp | 모델: $modelName")
    }

    private fun severityEmoji(severity: IssueSeverity): String = when (severity) {
        IssueSeverity.CRITICAL -> "🔴"
        IssueSeverity.MAJOR -> "🟠"
        IssueSeverity.MINOR -> "🟡"
        IssueSeverity.SUGGESTION -> "🔵"
    }
}
