package stillframe42.aicodereviewer.github.adapter.out.formatter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

class MarkdownReviewCommentFormatterTest {

    private val formatter = MarkdownReviewCommentFormatter(
        reviewerProperties = AiReviewerProperties(defaultModel = "default-model"),
    )

    @Test
    fun `이슈가 없고 인라인도 없으면 총 0개를 표시한다`() {
        val review = review(issues = emptyList())
        val result = formatter.format(review, totalIssueCount = 0, lineCommentCount = 0)
        assertThat(result).contains("Issues Found (총 0개)")
        assertThat(result).contains("발견된 이슈가 없습니다.")
    }

    @Test
    fun `인라인 코멘트만 있고 요약 이슈가 없으면 총 N개 — 인라인 N을 표시한다`() {
        val review = review(issues = emptyList())
        val result = formatter.format(review, totalIssueCount = 6, lineCommentCount = 6)
        assertThat(result).contains("Issues Found (총 6개 — 인라인 6)")
        assertThat(result).contains("발견된 이슈가 없습니다.")
    }

    @Test
    fun `요약 이슈만 있고 인라인이 없으면 총 N개 — 요약 N을 표시한다`() {
        val issues = listOf(issue())
        val review = review(issues = issues)
        val result = formatter.format(review, totalIssueCount = 1, lineCommentCount = 0)
        assertThat(result).contains("Issues Found (총 1개 — 요약 1)")
        assertThat(result).doesNotContain("발견된 이슈가 없습니다.")
    }

    @Test
    fun `인라인과 요약 모두 있으면 총 N개 — 인라인 M, 요약 K를 표시한다`() {
        val issues = listOf(issue(), issue())
        val review = review(issues = issues)
        val result = formatter.format(review, totalIssueCount = 8, lineCommentCount = 6)
        assertThat(result).contains("Issues Found (총 8개 — 인라인 6, 요약 2)")
    }

    @Test
    fun `기존 format(review) 시그니처는 총 이슈 수만 표시한다`() {
        val issues = listOf(issue())
        val review = review(issues = issues)
        val result = formatter.format(review)
        assertThat(result).contains("Issues Found (${issues.size})")
    }

    @Test
    fun `리뷰에 사용된 모델명이 있으면 footer에 그 모델을 표시한다`() {
        val review = review(issues = emptyList()).copy(modelName = "claude-sonnet-4-6")
        val result = formatter.format(review)
        assertThat(result).contains("모델: claude-sonnet-4-6")
    }

    @Test
    fun `리뷰에 모델명이 없으면 footer에 기본 모델을 표시한다`() {
        val review = review(issues = emptyList())
        val result = formatter.format(review)
        assertThat(result).contains("모델: default-model")
    }

    private fun review(issues: List<CodeIssue> = emptyList()) = CodeReview(
        overallScore = 7,
        summary = "테스트 요약",
        issues = issues,
        positives = listOf("좋은 점"),
    )

    private fun issue() = CodeIssue(
        id = "test-id",
        category = IssueCategory.SECURITY,
        filename = "Test.kt",
        line = 10,
        severity = IssueSeverity.MAJOR,
        description = "테스트 이슈",
        suggestion = "테스트 제안",
    )
}
