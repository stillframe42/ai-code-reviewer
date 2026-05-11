package stillframe42.aicodereviewer.agent.domain.service

import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.model.AgentFinding
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity
import java.util.UUID

object AgentFindingMapper {

    fun toCodeReview(result: AgentAnalysisResult): CodeReview =
        CodeReview(
            overallScore = computeOverallScore(result.findings),
            summary = "Python 에이전트 심층 분석 결과 (analysisId=${result.analysisId})",
            issues = result.findings.map { it.toCodeIssue() },
            positives = emptyList(),
        )

    private fun AgentFinding.toCodeIssue(): CodeIssue {
        val (filename, line) = parseLocation(location)
        return CodeIssue(
            id = UUID.randomUUID().toString(),
            category = IssueCategory.SECURITY,
            filename = filename,
            line = line,
            severity = mapSeverity(severity),
            description = description + (owaspReference?.let { " (OWASP: $it)" }.orEmpty()),
            suggestion = suggestion,
        )
    }

    private fun mapSeverity(raw: String): IssueSeverity =
        when (raw.uppercase()) {
            "CRITICAL", "HIGH" -> IssueSeverity.CRITICAL
            "MAJOR", "MEDIUM" -> IssueSeverity.MAJOR
            "MINOR", "LOW" -> IssueSeverity.MINOR
            "INFO", "SUGGESTION" -> IssueSeverity.SUGGESTION
            else -> IssueSeverity.SUGGESTION
        }

    private fun parseLocation(location: String): Pair<String?, Int?> {
        val colonIndex = location.lastIndexOf(':')
        if (colonIndex <= 0) return location.takeIf { it.isNotBlank() } to null
        val rawLine = location.substring(colonIndex + 1)
        val line = rawLine.toIntOrNull() ?: return location to null
        return location.substring(0, colonIndex) to line
    }

    private fun computeOverallScore(findings: List<AgentFinding>): Int {
        if (findings.isEmpty()) return 10
        val severityWeight = findings.sumOf { weightOf(mapSeverity(it.severity)) }
        return (10 - severityWeight.coerceAtMost(9)).coerceIn(1, 10)
    }

    private fun weightOf(severity: IssueSeverity): Int = when (severity) {
        IssueSeverity.CRITICAL -> 4
        IssueSeverity.MAJOR -> 3
        IssueSeverity.MINOR -> 2
        IssueSeverity.SUGGESTION -> 1
    }
}
