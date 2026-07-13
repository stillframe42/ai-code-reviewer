package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import stillframe42.aicodereviewer.common.advisor.CostTrackingAdvisor
import stillframe42.aicodereviewer.common.advisor.LoggingAdvisor
import stillframe42.aicodereviewer.common.port.CostLogPort

// Advisor 빈 등록 — 모든 어드바이저를 여기서 중앙 관리
@Configuration
@EnableConfigurationProperties(LlmCostProperties::class)
class AdvisorConfig {

    @Bean
    fun loggingAdvisor(): LoggingAdvisor = LoggingAdvisor()

    // API 오류 재시도는 공식 SDK 내장 재시도(기본 2회, Retry-After 존중)에 위임한다
    // — Spring AI 2.0 GA 에서 SDK 예외 체계가 바뀌며 자체 RetryAdvisor 는 제거됨

    @Bean
    fun costTrackingAdvisor(
        costProperties: LlmCostProperties,
        costLogPort: CostLogPort,
        eventPublisher: ApplicationEventPublisher,
    ): CostTrackingAdvisor = CostTrackingAdvisor(costProperties, costLogPort, eventPublisher)
}
