package stillframe42.aicodereviewer.github.adapter.out.formatter

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.port.out.ReviewCommentFormatterPort
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// CodeReview 결과를 GitHub PR 코멘트용 마크다운 문자열로 변환하는 어댑터
@Component
class MarkdownReviewCommentFormatter(
    @param:Value("\${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}")
    private val modelName: String,
) : ReviewCommentFormatterPort {

    // 기존 시그니처 — 하위 호환성 유지, 이슈 수는 review.issues.size 기준
    override fun format(review: CodeReview): String =
        formatBody(review, "Issues Found (${review.issues.size})")

    // 총/인라인/요약 수를 명시적으로 받는 오버로드
    override fun format(review: CodeReview, totalIssueCount: Int, lineCommentCount: Int): String {
        val unmappedCount = review.issues.size
        val label = buildIssuesLabel(totalIssueCount, lineCommentCount, unmappedCount)
        return formatBody(review, label)
    }

    // 이슈 라벨 문자열 생성: 총 N개 — 인라인 M, 요약 K 형식
    private fun buildIssuesLabel(total: Int, inline: Int, unmapped: Int): String {
        if (total == 0) return "Issues Found (총 0개)"
        val parts = mutableListOf<String>()
        if (inline > 0) parts.add("인라인 $inline")
        if (unmapped > 0) parts.add("요약 $unmapped")
        return if (parts.isEmpty()) "Issues Found (총 ${total}개)"
        else "Issues Found (총 ${total}개 — ${parts.joinToString(", ")})"
    }

    // 공통 마크다운 본문 렌더링
    private fun formatBody(review: CodeReview, issuesLabel: String): String = buildString {
        appendLine("## 🤖 AI Code Review")
        appendLine()

        appendLine("### 📋 Summary")
        appendLine()
        appendLine("> 종합 점수: ${review.overallScore}/10")
        appendLine()
        appendLine(review.summary)
        appendLine()

        appendLine("### 🚨 $issuesLabel")
        appendLine()
        if (review.issues.isEmpty()) {
            appendLine("발견된 이슈가 없습니다.")
        } else {
            appendLine("| Severity | Type | Description |")
            appendLine("|----------|------|-------------|")
            review.issues.forEach { issue ->
                val lineTag = issue.line?.let { " (L$it)" } ?: ""
                val description = "${issue.description}$lineTag<br>💡 ${issue.suggestion}"
                appendLine("| ${issue.severity.emoji} ${issue.severity} | ${issue.category} | $description |")
            }
        }
        appendLine()

        // 항목이 없으면 섹션 전체 생략, 최대 3개까지만 표시
        val positives = review.positives.take(3)
        if (positives.isNotEmpty()) {
            appendLine("### ✅ Positives")
            appendLine()
            positives.forEach { positive ->
                appendLine("- $positive")
            }
            appendLine()
        }

        val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        append("> 생성 시간: $timestamp | 모델: $modelName")
    }
}
