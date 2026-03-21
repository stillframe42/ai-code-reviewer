package stillframe42.aicodereviewer.review.benchmark

import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// 프롬프트 버전별 벤치마크 결과 수집 지표
data class PromptBenchmarkResult(
    val version: String,
    val fixtureName: String,
    val overallScore: Int,
    val issuesByCategory: Map<IssueCategory, Int>,
    val issuesBySeverity: Map<IssueSeverity, Int>,
    val positiveCount: Int,
    val systemPromptTokenEstimate: Int,
    val durationMs: Long,
    // 모든 이슈 필드(id/category/severity/description/suggestion)가 채워져 있는지 여부
    val allIssueFieldsPopulated: Boolean,
) {
    val totalIssues: Int get() = issuesBySeverity.values.sum()
    val criticalCount: Int get() = issuesBySeverity[IssueSeverity.CRITICAL] ?: 0
    val securityCount: Int get() = issuesByCategory[IssueCategory.SECURITY] ?: 0
    val performanceCount: Int get() = issuesByCategory[IssueCategory.PERFORMANCE] ?: 0
    val readabilityCount: Int get() = issuesByCategory[IssueCategory.READABILITY] ?: 0
    val architectureCount: Int get() = issuesByCategory[IssueCategory.ARCHITECTURE] ?: 0
}
