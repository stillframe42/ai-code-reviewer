package stillframe42.aicodereviewer.github.adapter.out.github.ratelimit

import java.time.Instant

data class RateLimitInfo(
    val remaining: Int,
    val resetAt: Instant,
)
