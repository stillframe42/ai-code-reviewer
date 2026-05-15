package stillframe42.aicodereviewer.agent.application

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentFallbackMetricsTest {

    @Test
    fun `record 호출 시 reason 태그가 붙은 카운터가 증가한다`() {
        val registry = SimpleMeterRegistry()
        val metrics = AgentFallbackMetrics(registry)

        metrics.record("unavailable")
        metrics.record("unavailable")
        metrics.record("timeout")
        metrics.record("failed")

        assertThat(registry.counter("agent.fallback.count", "reason", "unavailable").count())
            .isEqualTo(2.0)
        assertThat(registry.counter("agent.fallback.count", "reason", "timeout").count())
            .isEqualTo(1.0)
        assertThat(registry.counter("agent.fallback.count", "reason", "failed").count())
            .isEqualTo(1.0)
    }
}
