package stillframe42.aicodereviewer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 프로젝트 공통 Jackson ObjectMapper 빈 설정
// Spring Boot 4에서 ObjectMapper가 자동 구성 빈으로 등록되지 않는 경우를 대비한다.
// KotlinModule이 활성화된 ObjectMapper를 제공하며, 이미 다른 빈이 등록된 경우 무시된다.
@Configuration
class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()
}
