package stillframe42.aicodereviewer.common.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

@Component
class ReviewMetrics(private val meterRegistry: MeterRegistry) {

    fun startTimer(): Timer.Sample = Timer.start(meterRegistry)

    fun recordReview(sample: Timer.Sample, repo: String, status: String) {
        sample.stop(
            Timer.builder("review.duration")
                .tag("repo", repo)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
        )
        Counter.builder("review.requests.total")
            .tag("repo", repo)
            .tag("status", status)
            .register(meterRegistry)
            .increment()
    }

    fun recordIssues(issues: List<CodeIssue>) {
        issues.forEach { issue ->
            DistributionSummary.builder("review.issues.found")
                .tag("severity", issue.severity.toMetricTag())
                .register(meterRegistry)
                .record(1.0)
        }
    }

    // IssueSeverity를 메트릭 태그 레벨로 변환
    private fun IssueSeverity.toMetricTag(): String = when (this) {
        IssueSeverity.CRITICAL, IssueSeverity.MAJOR -> "HIGH"
        IssueSeverity.MINOR -> "MEDIUM"
        IssueSeverity.SUGGESTION -> "LOW"
    }
}
