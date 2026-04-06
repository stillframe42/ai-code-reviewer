package stillframe42.aicodereviewer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    // Spring Boot는 ReactiveStringRedisTemplate(ReactiveRedisTemplate<String,String> 서브타입)을 자동 구성한다.
    // @Primary로 명시해야 주입 시 두 빈 간 충돌을 방지할 수 있다.
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

    // Spring Boot 4에서 ObjectMapper 자동 구성이 빈으로 등록되지 않는 경우 대비 —
    // KotlinModule이 활성화된 ObjectMapper를 제공한다.
    // 이미 다른 ObjectMapper 빈이 등록된 경우 이 빈은 무시된다.
    @Bean
    @ConditionalOnMissingBean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()
}
