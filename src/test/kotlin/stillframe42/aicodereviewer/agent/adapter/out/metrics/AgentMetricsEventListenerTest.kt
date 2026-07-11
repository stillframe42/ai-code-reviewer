package stillframe42.aicodereviewer.agent.adapter.out.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.agent.application.AgentFallbackMetrics
import stillframe42.aicodereviewer.agent.domain.model.AgentFallbackEvent

class AgentMetricsEventListenerTest {

    @Test
    fun `AgentFallbackEvent 수신 시 reason 태그 카운터가 증가한다`() {
        val registry = SimpleMeterRegistry()
        val listener = AgentMetricsEventListener(AgentFallbackMetrics(registry))

        listener.onAgentFallback(AgentFallbackEvent("unavailable"))
        listener.onAgentFallback(AgentFallbackEvent("error"))
        listener.onAgentFallback(AgentFallbackEvent("error"))

        assertThat(registry.counter("agent.fallback.count", "reason", "unavailable").count())
            .isEqualTo(1.0)
        assertThat(registry.counter("agent.fallback.count", "reason", "error").count())
            .isEqualTo(2.0)
    }
}
