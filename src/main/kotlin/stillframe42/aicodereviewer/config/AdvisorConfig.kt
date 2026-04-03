package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import stillframe42.aicodereviewer.common.advisor.CostTrackingAdvisor
import stillframe42.aicodereviewer.common.advisor.LoggingAdvisor
import stillframe42.aicodereviewer.common.advisor.RetryAdvisor
import stillframe42.aicodereviewer.review.adapter.out.persistence.LlmCostLogRepository

// Advisor 빈 등록 — 모든 어드바이저를 여기서 중앙 관리
@Configuration
@EnableConfigurationProperties(LlmCostProperties::class)
class AdvisorConfig {

    @Bean
    fun loggingAdvisor(): LoggingAdvisor = LoggingAdvisor()

    @Bean
    fun retryAdvisor(): RetryAdvisor = RetryAdvisor()

    @Bean
    fun costTrackingAdvisor(
        costProperties: LlmCostProperties,
        costLogRepository: LlmCostLogRepository,
    ): CostTrackingAdvisor = CostTrackingAdvisor(costProperties, costLogRepository)
}
