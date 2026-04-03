package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import stillframe42.aicodereviewer.common.advisor.CostTrackingAdvisor
import stillframe42.aicodereviewer.review.adapter.out.persistence.LlmCostLogRepository

@Configuration
@EnableConfigurationProperties(LlmCostProperties::class)
class AdvisorConfig {

    // CostTrackingAdvisor를 스프링 빈으로 등록 — 통합 테스트에서 @Autowired 가능
    @Bean
    fun costTrackingAdvisor(
        costProperties: LlmCostProperties,
        costLogRepository: LlmCostLogRepository,
    ): CostTrackingAdvisor = CostTrackingAdvisor(costProperties, costLogRepository)
}
