package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

@ConfigurationProperties(prefix = "agent.remote")
data class RemoteAgentProperties(
    val url: String,
    val connectTimeout: Duration,
    val readTimeout: Duration,
    val maxInMemorySize: DataSize,
    val poll: PollProperties,
    val callback: CallbackProperties,
) {
    data class PollProperties(
        val maxAttempts: Int,
        val interval: Duration,
        val timeout: Duration,
    )

    data class CallbackProperties(
        val internalAuthToken: String,
    )
}
