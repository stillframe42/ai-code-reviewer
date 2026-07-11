package stillframe42.aicodereviewer.agent.adapter.out.metrics

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.agent.domain.model.AgentFallbackEvent

// ReviewMetricsEventListener 와 같은 패턴 — feature 소유 메트릭은 feature 가 수신한다
@Component
class AgentMetricsEventListener(
    private val agentFallbackMetrics: AgentFallbackMetrics,
) {

    @EventListener
    fun onAgentFallback(event: AgentFallbackEvent) {
        agentFallbackMetrics.record(event.reason)
    }
}
