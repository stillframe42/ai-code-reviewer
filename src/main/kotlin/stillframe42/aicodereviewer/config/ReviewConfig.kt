package stillframe42.aicodereviewer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ReviewProperties::class)
class ReviewConfig {

    // Jackson 2.x ObjectMapper — Kotlin 모듈 등록 (ReviewPersistenceAdapter 직렬화용)
    // Spring Boot 자동 구성 빈(objectMapper)과 충돌 방지를 위해 명시적 이름 지정
    @Bean("jackson2ObjectMapper")
    fun jackson2ObjectMapper(): ObjectMapper = ObjectMapper().registerKotlinModule()
}
