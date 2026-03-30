package stillframe42.aicodereviewer.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import stillframe42.aicodereviewer.github.adapter.out.github.ratelimit.GitHubRateLimitState
import java.time.Duration
import java.time.Instant

@Configuration
@EnableConfigurationProperties(GitHubProperties::class)
class GitHubConfig {

    // Webhook fire-and-forget 처리용 애플리케이션 코루틴 스코프
    // SupervisorJob: 개별 코루틴 실패가 스코프 전체를 취소하지 않도록 함
    @Bean("applicationScope")
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // GitHub REST API 호출용 WebClient — 응답 타임아웃 30초
    // ExchangeFilter로 Rate Limit 헤더를 자동으로 캡처한다
    @Bean("gitHubWebClient")
    fun gitHubWebClient(properties: GitHubProperties, rateLimitState: GitHubRateLimitState): WebClient {
        val httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(30))
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(properties.api.baseUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader("User-Agent", "ai-code-reviewer")
            .filter(rateLimitFilter(rateLimitState))
            .build()
    }

    // 응답 헤더 X-RateLimit-Remaining, X-RateLimit-Reset을 파싱하여 GitHubRateLimitState에 업데이트한다
    // request attribute "installationId"가 없는 요청(토큰 발급 등)은 스킵한다
    // X-RateLimit-Reset은 Unix epoch 초 단위 값이다
    private fun rateLimitFilter(rateLimitState: GitHubRateLimitState): ExchangeFilterFunction =
        ExchangeFilterFunction { request, next ->
            next.exchange(request).doOnNext { response ->
                val installationId = request.attribute("installationId")
                    .map { it as Long }
                    .orElse(null) ?: return@doOnNext
                val remaining = response.headers().header("X-RateLimit-Remaining")
                    .firstOrNull()?.toIntOrNull() ?: return@doOnNext
                val resetEpoch = response.headers().header("X-RateLimit-Reset")
                    .firstOrNull()?.toLongOrNull() ?: return@doOnNext
                rateLimitState.update(installationId, remaining, Instant.ofEpochSecond(resetEpoch))
            }
        }
}
