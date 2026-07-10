package stillframe42.aicodereviewer.review.adapter.out.metrics

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.review.domain.model.ReviewCompletedEvent

// MetricsEventListener 와 같은 패턴 — feature 소유 메트릭은 feature 가 수신한다
@Component
class ReviewMetricsEventListener(
    private val reviewMetrics: ReviewMetrics,
) {

    @EventListener
    fun onReviewCompleted(event: ReviewCompletedEvent) {
        reviewMetrics.recordReview(event.repo, event.status, event.durationNanos)
        reviewMetrics.recordIssues(event.issues)
    }
}
