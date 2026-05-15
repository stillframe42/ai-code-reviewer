package stillframe42.aicodereviewer.config

import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableConfigurationProperties(RemoteAgentProperties::class)
class RemoteAgentConfig {

    // 어댑터의 SnakeCase 직렬화 계약(@JsonNaming 의존) 을 codec layer 에서도 보장하기 위해
    // 전용 JsonMapper 에 SNAKE_CASE 를 글로벌 설정한다
    @Bean("remoteAgentWebClient")
    fun remoteAgentWebClient(properties: RemoteAgentProperties): WebClient {
        val jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build()
        val httpClient = HttpClient.create()
            .responseTimeout(properties.readTimeout)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout.toMillis().toInt())
        return WebClient.builder()
            .baseUrl(properties.url)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs {
                it.defaultCodecs().maxInMemorySize(properties.maxInMemorySize.toBytes().toInt())
                it.defaultCodecs().jacksonJsonEncoder(
                    JacksonJsonEncoder(jsonMapper, MediaType.APPLICATION_JSON),
                )
                it.defaultCodecs().jacksonJsonDecoder(
                    JacksonJsonDecoder(jsonMapper, MediaType.APPLICATION_JSON),
                )
            }
            .build()
    }
}
