package stillframe42.aicodereviewer.github.adapter.out.github.ratelimit

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.model.GitHubRateLimit
import stillframe42.aicodereviewer.github.domain.port.out.GitHubRateLimitPort

@Component
class GitHubRateLimitAdapter(
    private val rateLimitState: GitHubRateLimitState,
) : GitHubRateLimitPort {

    override fun currentRateLimit(installationId: Long): GitHubRateLimit? =
        rateLimitState.getRemainingOrNull(installationId)
            ?.let { GitHubRateLimit(remaining = it.remaining, resetAt = it.resetAt) }
}
