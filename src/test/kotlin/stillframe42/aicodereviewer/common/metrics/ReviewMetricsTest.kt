package stillframe42.aicodereviewer.common.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

class ReviewMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: ReviewMetrics

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        metrics = ReviewMetrics(registry)
    }

    @Test
    fun `리뷰 성공 시 review_requests_total DONE 카운터가 증가한다`() {
        val sample = metrics.startTimer()
        metrics.recordReview(sample, "owner/repo", "DONE")

        val count = registry.get("review.requests.total")
            .tag("repo", "owner/repo")
            .tag("status", "DONE")
            .counter()
            .count()

        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `리뷰 실패 시 review_requests_total FAILED 카운터가 증가한다`() {
        val sample = metrics.startTimer()
        metrics.recordReview(sample, "owner/repo", "FAILED")

        val count = registry.get("review.requests.total")
            .tag("repo", "owner/repo")
            .tag("status", "FAILED")
            .counter()
            .count()

        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `review_duration 타이머가 기록된다`() {
        val sample = metrics.startTimer()
        metrics.recordReview(sample, "owner/repo", "DONE")

        val timer = registry.get("review.duration")
            .tag("repo", "owner/repo")
            .timer()

        assertThat(timer.count()).isEqualTo(1L)
    }

    @Test
    fun `CRITICAL과 MAJOR 이슈는 HIGH로 매핑된다`() {
        val issues = listOf(
            CodeIssue("1", IssueCategory.SECURITY, null, 1, IssueSeverity.CRITICAL, "desc", "sug"),
            CodeIssue("2", IssueCategory.PERFORMANCE, null, 2, IssueSeverity.MAJOR, "desc", "sug"),
        )

        metrics.recordIssues(issues)

        val count = registry.get("review.issues.found")
            .tag("severity", "HIGH")
            .summary()
            .count()

        assertThat(count).isEqualTo(2L)
    }

    @Test
    fun `MINOR 이슈는 MEDIUM으로 매핑된다`() {
        metrics.recordIssues(listOf(
            CodeIssue("3", IssueCategory.READABILITY, null, 5, IssueSeverity.MINOR, "desc", "sug"),
        ))

        val count = registry.get("review.issues.found")
            .tag("severity", "MEDIUM")
            .summary()
            .count()

        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `SUGGESTION 이슈는 LOW로 매핑된다`() {
        metrics.recordIssues(listOf(
            CodeIssue("4", IssueCategory.ARCHITECTURE, null, 10, IssueSeverity.SUGGESTION, "desc", "sug"),
        ))

        val count = registry.get("review.issues.found")
            .tag("severity", "LOW")
            .summary()
            .count()

        assertThat(count).isEqualTo(1L)
    }
}
