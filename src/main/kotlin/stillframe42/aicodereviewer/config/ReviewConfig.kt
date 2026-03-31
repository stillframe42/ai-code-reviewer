package stillframe42.aicodereviewer.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ReviewProperties::class)
class ReviewConfig {

    // ObjectMapper 빈 — Jackson 직렬화/역직렬화용
    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper()
}
