package stillframe42.aicodereviewer.config

import io.micrometer.observation.ObservationRegistry
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

    // ObservationRegistry에 핸들러를 등록 — Spring AI LLM 호출이 자동으로 핸들러로 라우팅됨
    @Bean
    fun langfuseObservationHandler(
        langfuseClient: LangfuseClient,
        observationRegistry: ObservationRegistry,
    ): LangfuseObservationHandler {
        val handler = LangfuseObservationHandler(langfuseClient)
        observationRegistry.observationConfig().observationHandler(handler)
        return handler
    }
}
