package stillframe42.aicodereviewer.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import stillframe42.aicodereviewer.review.adapter.out.ai.NoopToolObservationAdapter
import stillframe42.aicodereviewer.review.domain.port.out.ToolObservationPort

// ToolObservationPort 폴백 설정 — 다른 구현체(LangfuseToolSpanAdapter)가 없을 때만 Noop 등록
@Configuration
class ToolObservationConfig {

    @Bean
    @ConditionalOnMissingBean(ToolObservationPort::class)
    fun noopToolObservationAdapter(): ToolObservationPort = NoopToolObservationAdapter()
}
