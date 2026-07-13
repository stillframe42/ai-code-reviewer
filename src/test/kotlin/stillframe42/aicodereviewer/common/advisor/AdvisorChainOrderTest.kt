package stillframe42.aicodereviewer.common.advisor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor
import org.springframework.core.Ordered

// Spring AI 2.0 이 자동 등록하는 ToolCallingAdvisor(tool loop)보다 커스텀 advisor 가 바깥에 위치해야
// Logging/CostTracking 이 "최종 교환 1회만 관찰" 하는 계약이 유지된다 — 프레임워크 order 변경 감지용
class AdvisorChainOrderTest {

    @Test
    fun `커스텀 advisor 는 자동 등록 ToolCallingAdvisor 보다 바깥에 위치한다`() {
        assertThat(LoggingAdvisor().order).isLessThan(ToolCallingAdvisor.DEFAULT_ORDER)
        // CostTrackingAdvisor 기본 order (Ordered.HIGHEST_PRECEDENCE + 2)
        assertThat(Ordered.HIGHEST_PRECEDENCE + 2).isLessThan(ToolCallingAdvisor.DEFAULT_ORDER)
    }
}
