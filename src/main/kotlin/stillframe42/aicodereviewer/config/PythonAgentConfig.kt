package stillframe42.aicodereviewer.config

import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class PythonAgentConfig {

    @Bean("pythonAgentWebClient")
    fun pythonAgentWebClient(
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
            .codecs { it.defaultCodecs().maxInMemorySize(maxInMemorySize.toBytes().toInt()) }
            .build()
    }
}
