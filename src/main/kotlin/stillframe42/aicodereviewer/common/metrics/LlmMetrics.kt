package stillframe42.aicodereviewer.common.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class LlmMetrics(private val meterRegistry: MeterRegistry) {

    fun recordTokens(model: String, promptTokens: Int, completionTokens: Int) {
        Counter.builder("llm.tokens.used")
            .tag("model", model)
            .tag("type", "prompt")
            .register(meterRegistry)
            .increment(promptTokens.toDouble())
        Counter.builder("llm.tokens.used")
            .tag("model", model)
            .tag("type", "completion")
            .register(meterRegistry)
            .increment(completionTokens.toDouble())
    }

    fun recordCost(model: String, costUsd: BigDecimal) {
        val costMicro = costUsd.multiply(BigDecimal(1_000_000)).toLong()
        Counter.builder("llm.cost.total")
            .tag("model", model)
            .register(meterRegistry)
            .increment(costMicro.toDouble())
    }
}
