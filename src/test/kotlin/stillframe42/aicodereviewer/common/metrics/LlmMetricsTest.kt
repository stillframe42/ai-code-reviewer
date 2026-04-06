package stillframe42.aicodereviewer.common.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LlmMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: LlmMetrics

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        metrics = LlmMetrics(registry)
    }

    @Test
    fun `prompt 토큰 카운터가 증가한다`() {
        metrics.recordTokens("claude-haiku-4-5-20251001", promptTokens = 100, completionTokens = 50)

        val count = registry.get("llm.tokens.used")
            .tag("model", "claude-haiku-4-5-20251001")
            .tag("type", "prompt")
            .counter()
            .count()

        assertThat(count).isEqualTo(100.0)
    }

    @Test
    fun `completion 토큰 카운터가 증가한다`() {
        metrics.recordTokens("claude-haiku-4-5-20251001", promptTokens = 100, completionTokens = 50)

        val count = registry.get("llm.tokens.used")
            .tag("model", "claude-haiku-4-5-20251001")
            .tag("type", "completion")
            .counter()
            .count()

        assertThat(count).isEqualTo(50.0)
    }

    @Test
    fun `비용이 마이크로달러 정수로 기록된다`() {
        // 0.000280 USD → 280 마이크로달러
        metrics.recordCost("claude-haiku-4-5-20251001", BigDecimal("0.000280"))

        val count = registry.get("llm.cost.total")
            .tag("model", "claude-haiku-4-5-20251001")
            .counter()
            .count()

        assertThat(count).isEqualTo(280.0)
    }

    @Test
    fun `같은 모델 호출이 누적된다`() {
        metrics.recordTokens("claude-haiku-4-5-20251001", promptTokens = 100, completionTokens = 50)
        metrics.recordTokens("claude-haiku-4-5-20251001", promptTokens = 200, completionTokens = 30)

        val count = registry.get("llm.tokens.used")
            .tag("model", "claude-haiku-4-5-20251001")
            .tag("type", "prompt")
            .counter()
            .count()

        assertThat(count).isEqualTo(300.0)
    }

    @Test
    fun `소수점 이하 마이크로달러는 반올림된다`() {
        // 0.0000015 USD → 1.5 마이크로달러 → 반올림 → 2
        metrics.recordCost("claude-haiku-4-5-20251001", BigDecimal("0.0000015"))

        val count = registry.get("llm.cost.total")
            .tag("model", "claude-haiku-4-5-20251001")
            .counter()
            .count()

        assertThat(count).isEqualTo(2.0)
    }
}
