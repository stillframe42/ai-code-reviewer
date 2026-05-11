package stillframe42.aicodereviewer.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class PythonAgentConfig {

    // Spring Boot 가 구성한 ObjectMapper 를 WebClient codec 에 명시적으로 주입한다 —
    // DTO 의 @JsonNaming(SnakeCase) / KotlinModule 등 어플리케이션 설정을 그대로 사용하기 위함
    @Bean("pythonAgentWebClient")
    fun pythonAgentWebClient(
        objectMapper: ObjectMapper,
        @Value("\${agent.python.url}") agentUrl: String,
        @Value("\${agent.python.connect-timeout}") connectTimeout: Duration,
        @Value("\${agent.python.read-timeout}") readTimeout: Duration,
        @Value("\${agent.python.max-in-memory-size}") maxInMemorySize: DataSize,
    ): WebClient {
        val httpClient = HttpClient.create()
            .responseTimeout(readTimeout)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())
        return WebClient.builder()
            .baseUrl(agentUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs {
                it.defaultCodecs().maxInMemorySize(maxInMemorySize.toBytes().toInt())
                it.defaultCodecs().jackson2JsonEncoder(
                    Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON),
                )
                it.defaultCodecs().jackson2JsonDecoder(
                    Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON),
                )
            }
            .build()
    }
}
