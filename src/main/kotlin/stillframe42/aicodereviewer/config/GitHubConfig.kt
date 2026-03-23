package stillframe42.aicodereviewer.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(GitHubProperties::class)
class GitHubConfig {

    // Webhook fire-and-forget 처리용 애플리케이션 코루틴 스코프
    // SupervisorJob: 개별 코루틴 실패가 스코프 전체를 취소하지 않도록 함
    @Bean("applicationScope")
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
