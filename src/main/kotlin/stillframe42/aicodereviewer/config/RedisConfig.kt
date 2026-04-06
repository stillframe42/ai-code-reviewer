package stillframe42.aicodereviewer.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

// 전체 프로젝트 공통 Redis 빈 설정
// key/value 모두 String 직렬화 — value는 각 어댑터에서 JSON 문자열로 변환 후 저장
@Configuration
class RedisConfig {

    @Bean
    @Primary
    fun reactiveRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory,
    ): ReactiveRedisTemplate<String, String> {
        val serializer = StringRedisSerializer()
        val context = RedisSerializationContext
            .newSerializationContext<String, String>(serializer)
            .build()
        return ReactiveRedisTemplate(connectionFactory, context)
    }

    // ObjectMapper가 자동설정되지 않은 경우 대비
    @Bean
    @ConditionalOnMissingBean
    fun objectMapper(): ObjectMapper = ObjectMapper()
}
