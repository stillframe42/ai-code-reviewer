package stillframe42.aicodereviewer.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import stillframe42.aicodereviewer.common.langfuse.LangfuseClient
import stillframe42.aicodereviewer.common.langfuse.LangfuseObservationHandler
import stillframe42.aicodereviewer.review.adapter.out.ai.LangfuseToolSpanAdapter
import stillframe42.aicodereviewer.review.domain.port.out.ToolObservationPort

// Langfuse 관찰성 빈 등록 — langfuse.enabled=false 이면 전체 미등록
@Configuration
@EnableConfigurationProperties(LangfuseProperties::class)
@ConditionalOnProperty(name = ["langfuse.enabled"], matchIfMissing = true)
class LangfuseObservationConfig {

    @Bean
    fun langfuseClient(properties: LangfuseProperties): LangfuseClient =
        LangfuseClient(properties)

    @Bean
    fun langfuseToolSpanAdapter(langfuseClient: LangfuseClient): ToolObservationPort =
        LangfuseToolSpanAdapter(langfuseClient)

    // ObservationHandler 빈으로 등록 — Spring Boot의 ObservationHandlerGroupingCustomizer가
    // 자동으로 ObservationRegistry에 추가한다. ObservationRegistry 직접 주입 시 순환 의존성 발생.
    @Bean
    fun langfuseObservationHandler(langfuseClient: LangfuseClient): LangfuseObservationHandler =
        LangfuseObservationHandler(langfuseClient)
}
