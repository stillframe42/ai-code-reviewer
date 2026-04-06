package stillframe42.aicodereviewer.common.metrics

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.metrics.event.LlmCallCompletedEvent
import stillframe42.aicodereviewer.common.metrics.event.ReviewCompletedEvent

// 메트릭 이벤트 리스너 — 비즈니스 코드에서 발행된 이벤트를 수신해 Micrometer에 기록
// 새 메트릭이 필요하면 리스너 메서드를 추가하면 되고, 비즈니스 코드는 수정하지 않아도 된다
@Component
class MetricsEventListener(
    private val reviewMetrics: ReviewMetrics,
    private val llmMetrics: LlmMetrics,
) {

    @EventListener
    fun onReviewCompleted(event: ReviewCompletedEvent) {
        reviewMetrics.recordReview(event.repo, event.status, event.durationNanos)
        reviewMetrics.recordIssues(event.issues)
    }

    @EventListener
    fun onLlmCallCompleted(event: LlmCallCompletedEvent) {
        llmMetrics.recordTokens(event.model, event.promptTokens, event.completionTokens)
        llmMetrics.recordCost(event.model, event.costUsd)
    }
}
