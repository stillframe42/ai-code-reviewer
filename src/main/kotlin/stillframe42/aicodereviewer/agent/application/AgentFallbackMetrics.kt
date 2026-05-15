package stillframe42.aicodereviewer.agent.application

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

// Python agent 폴백 발생 횟수를 reason 태그별로 집계한다.
// reason 값은 호출자가 결정 — 현재는 unavailable / timeout / failed 3종으로 한정한다 (cardinality 안전).
@Component
class AgentFallbackMetrics(
    private val meterRegistry: MeterRegistry,
) {
    fun record(reason: String) {
        Counter.builder("agent.fallback.count")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }
}
