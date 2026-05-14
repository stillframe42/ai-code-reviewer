package stillframe42.aicodereviewer.review.comparison

import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// Simple 모드와 WithGitHubTools 모드 비교 결과를 마크다운으로 생성하는 유틸
object ComparisonReportWriter {

    // claude-haiku-4-5-20251001 기준 출력 토큰 단가 (2026-04 시점, $4.00/MTok)
    // 요금은 변경될 수 있으므로 보고서에 측정 시점 모델명과 함께 기재됩니다
    private const val OUTPUT_COST_PER_TOKEN = 4.0 / 1_000_000

    fun generate(
        simpleResult: ComparisonResult,
        toolsResult: ComparisonResult,
        repo: String,
        prNumber: Int,
    ): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val simpleCost = simpleResult.estimatedOutputTokens * OUTPUT_COST_PER_TOKEN
        val toolsCost = toolsResult.estimatedOutputTokens * OUTPUT_COST_PER_TOKEN

        val simpleByCategory = simpleResult.review.issues.groupingBy { it.category }.eachCount()
        val toolsByCategory = toolsResult.review.issues.groupingBy { it.category }.eachCount()

        val simpleAvgDescLen = simpleResult.review.issues
            .map { it.description.length }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toInt() ?: 0
        val toolsAvgDescLen = toolsResult.review.issues
            .map { it.description.length }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toInt() ?: 0

        val issueDiff = toolsResult.review.issues.size - simpleResult.review.issues.size
        val issueDiffStr = if (issueDiff >= 0) "+$issueDiff" else "$issueDiff"
        val latencyDiff = toolsResult.latencyMs - simpleResult.latencyMs

        return buildString {
            appendLine("# 코드 리뷰 품질 비교: WITHOUT_TOOLS vs WITH_TOOLS")
            appendLine()
            appendLine("## 테스트 환경")
            appendLine()
            appendLine("| 항목 | 값 |")
            appendLine("|---|---|")
            appendLine("| 레포 | `$repo` |")
            appendLine("| PR 번호 | #$prNumber |")
            appendLine("| 실행 일시 | $now |")
            appendLine("| 모델 | claude-haiku-4-5-20251001 |")
            appendLine()
            appendLine("## 요약 비교표")
            appendLine()
            appendLine("| 항목 | WITHOUT_TOOLS | WITH_TOOLS |")
            appendLine("|---|---|---|")
            appendLine("| 이슈 감지 수 | ${simpleResult.review.issues.size} | ${toolsResult.review.issues.size} |")
            appendLine("| Tool 호출 횟수 | ${simpleResult.review.toolCallCount} | ${toolsResult.review.toolCallCount} |")
            appendLine("| 응답 토큰 추정값 (출력) \\* | ${simpleResult.estimatedOutputTokens} | ${toolsResult.estimatedOutputTokens} |")
            appendLine("| 응답 시간 (ms) | ${simpleResult.latencyMs} | ${toolsResult.latencyMs} |")
            appendLine("| 비용 추정 (USD) \\* | \$${"%.6f".format(simpleCost)} | \$${"%.6f".format(toolsCost)} |")
            appendLine()
            appendLine("> \\* 토큰/비용은 응답 텍스트 기반 근사값 (4자 ≈ 1토큰). Tool 호출 입력 토큰은 미포함.")
            appendLine()
            appendLine("## 카테고리별 이슈 분포")
            appendLine()
            appendLine("| 카테고리 | WITHOUT_TOOLS | WITH_TOOLS |")
            appendLine("|---|---|---|")
            IssueCategory.entries.forEach { category ->
                appendLine("| $category | ${simpleByCategory[category] ?: 0} | ${toolsByCategory[category] ?: 0} |")
            }
            appendLine()
            appendLine("## 리뷰 구체성 지표")
            appendLine()
            appendLine("| 지표 | WITHOUT_TOOLS | WITH_TOOLS |")
            appendLine("|---|---|---|")
            appendLine("| summary 길이 (문자) | ${simpleResult.review.summary.length} | ${toolsResult.review.summary.length} |")
            appendLine("| issue description 평균 길이 | $simpleAvgDescLen | $toolsAvgDescLen |")
            appendLine()
            appendLine("## 관찰 포인트")
            appendLine()
            appendLine("- 이슈 감지: WITH_TOOLS 모드에서 $issueDiffStr 건 차이")
            if (toolsResult.review.toolCallCount > 0) {
                appendLine("- Tool 호출: ${toolsResult.review.toolCallCount}회 호출로 추가 컨텍스트 수집")
            }
            appendLine("- 응답 시간: WITH_TOOLS 모드가 ${latencyDiff}ms 더 소요")
            when {
                toolsAvgDescLen > simpleAvgDescLen ->
                    appendLine("- 구체성: WITH_TOOLS 모드의 이슈 설명이 평균 ${toolsAvgDescLen - simpleAvgDescLen}자 더 상세함")
                simpleAvgDescLen > toolsAvgDescLen ->
                    appendLine("- 구체성: WITHOUT_TOOLS 모드의 이슈 설명이 평균 ${simpleAvgDescLen - toolsAvgDescLen}자 더 상세함")
                else ->
                    appendLine("- 구체성: 두 모드의 이슈 설명 길이가 유사함")
            }
        }
    }

    fun writeTo(path: Path, content: String) {
        path.parent?.createDirectories()
        path.writeText(content)
    }
}
