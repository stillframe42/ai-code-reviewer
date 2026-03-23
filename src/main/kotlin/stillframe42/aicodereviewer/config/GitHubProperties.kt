package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "github")
data class GitHubProperties(
    val app: AppProperties,
    val api: ApiProperties = ApiProperties(),
) {
    data class AppProperties(
        val privateKeyPath: String,
        val appId: Long,
        val webhookSecret: String,
    )

    data class ApiProperties(
        val baseUrl: String = "https://api.github.com",
    )
}
