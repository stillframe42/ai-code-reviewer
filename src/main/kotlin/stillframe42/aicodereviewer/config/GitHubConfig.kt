package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import stillframe42.aicodereviewer.github.adapter.out.github.ratelimit.GitHubRateLimitState
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(GitHubProperties::class)
class GitHubConfig {

    // Webhook fire-and-forget 처리용 가상 스레드 executor — 태스크별 새 가상 스레드 생성
    @Bean("applicationExecutor")
    fun applicationExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    // GitHub REST API 호출용 RestClient — 응답 타임아웃 30초
    // 인터셉터로 Rate Limit 헤더를 자동으로 캡처한다
    @Bean("gitHubRestClient")
    fun gitHubRestClient(properties: GitHubProperties, rateLimitState: GitHubRateLimitState): RestClient {
        val requestFactory = JdkClientHttpRequestFactory()
        requestFactory.setReadTimeout(Duration.ofSeconds(30))
        return RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.api.baseUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader("User-Agent", "ai-code-reviewer")
            .requestInterceptor(rateLimitInterceptor(rateLimitState))
            .build()
    }

    // 응답 헤더 X-RateLimit-Remaining, X-RateLimit-Reset을 파싱하여 GitHubRateLimitState에 업데이트한다
    // request attribute "installationId"가 없는 요청(토큰 발급 등)은 스킵한다
    // X-RateLimit-Reset은 Unix epoch 초 단위 값이다
    private fun rateLimitInterceptor(rateLimitState: GitHubRateLimitState): ClientHttpRequestInterceptor =
        ClientHttpRequestInterceptor { request, body, execution ->
            val response = execution.execute(request, body)
            val installationId = request.attributes["installationId"] as? Long
            if (installationId != null) {
                val remaining = response.headers.getFirst("X-RateLimit-Remaining")?.toIntOrNull()
                val resetEpoch = response.headers.getFirst("X-RateLimit-Reset")?.toLongOrNull()
                if (remaining != null && resetEpoch != null) {
                    rateLimitState.update(installationId, remaining, Instant.ofEpochSecond(resetEpoch))
                }
            }
            response
        }
}
