package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(GitHubProperties::class)
class GitHubConfig {

    // GitHub REST API 호출용 WebClient
    @Bean("gitHubWebClient")
    fun gitHubWebClient(properties: GitHubProperties): WebClient =
        WebClient.builder()
            .baseUrl(properties.api.baseUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader("User-Agent", "ai-code-reviewer")
            .build()
}
