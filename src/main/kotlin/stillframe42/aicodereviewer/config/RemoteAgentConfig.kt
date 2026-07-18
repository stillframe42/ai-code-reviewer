package stillframe42.aicodereviewer.config

import io.micrometer.observation.ObservationRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

@Configuration
@EnableConfigurationProperties(RemoteAgentProperties::class)
class RemoteAgentConfig {

    // 어댑터의 SnakeCase 직렬화 계약(@JsonNaming 의존) 을 컨버터 layer 에서도 보장하기 위해
    // 전용 JsonMapper 에 SNAKE_CASE 를 글로벌 설정한다
    @Bean("remoteAgentRestClient")
    fun remoteAgentRestClient(
        properties: RemoteAgentProperties,
        observationRegistry: ObservationRegistry,
    ): RestClient {
        val jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build()
        // ai-agent-service 는 h2c(cleartext HTTP/2) 를 지원하지 않으므로 HTTP/1.1 로 고정한다 —
        // 기본 HTTP_2 preference 로는 커넥션 재사용 중 5xx/fault 응답 이후 RST_STREAM 으로 이어짐
        val requestFactory = JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout)
                .build(),
        )
        requestFactory.setReadTimeout(properties.readTimeout)
        return RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.url)
            // ai-agent-service 의 /agent 라우터는 X-Internal-Auth 를 요구 — 콜백과 같은 공유 비밀 사용
            .defaultHeader("X-Internal-Auth", properties.callback.internalAuthToken)
            .observationRegistry(observationRegistry)
            .messageConverters { converters ->
                converters.removeIf { it is JacksonJsonHttpMessageConverter }
                converters.add(0, JacksonJsonHttpMessageConverter(jsonMapper))
            }
            .build()
    }
}
