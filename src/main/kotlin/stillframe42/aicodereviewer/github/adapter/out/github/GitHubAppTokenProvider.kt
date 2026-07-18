package stillframe42.aicodereviewer.github.adapter.out.github

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort
import stillframe42.aicodereviewer.github.adapter.out.github.client.GitHubHttpClient
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// GitHub App Installation Access Token 발급 및 캐싱
@Component
class GitHubAppTokenProvider(
    private val gitHubHttpClient: GitHubHttpClient,
    private val jwtGenerator: GitHubAppJwtGenerator,
) : GitHubTokenPort {

    // 토큰 캐시 — installationId → CachedToken
    private val tokenCache = ConcurrentHashMap<Long, CachedToken>()

    override fun getInstallationToken(installationId: Long): String {
        // 유효한 캐시가 있으면 즉시 반환
        tokenCache[installationId]
            ?.takeUnless { it.isExpiredOrExpiringSoon() }
            ?.let { return it.token }

        return fetchAndCacheToken(installationId)
    }

    private fun fetchAndCacheToken(installationId: Long): String {
        val jwt = jwtGenerator.generate()
        val response = gitHubHttpClient.fetchInstallationToken(installationId, jwt)

        val cached = CachedToken(
            token = response.token,
            expiresAt = Instant.parse(response.expiresAt),
        )
        tokenCache[installationId] = cached
        return response.token
    }

    // 만료 5분 전이면 갱신 대상으로 판단
    internal data class CachedToken(val token: String, val expiresAt: Instant) {
        fun isExpiredOrExpiringSoon(): Boolean =
            Instant.now().isAfter(expiresAt.minusSeconds(300))
    }
}
