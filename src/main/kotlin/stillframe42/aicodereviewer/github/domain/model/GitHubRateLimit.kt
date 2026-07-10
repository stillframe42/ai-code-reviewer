package stillframe42.aicodereviewer.github.domain.model

import java.time.Instant

data class GitHubRateLimit(
    val remaining: Int,
    val resetAt: Instant,
)
