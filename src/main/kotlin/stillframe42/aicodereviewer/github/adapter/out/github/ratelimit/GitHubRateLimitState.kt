package stillframe42.aicodereviewer.github.adapter.out.github.ratelimit

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// GitHub API Rate Limit 상태를 installationId 단위로 관리한다
// ConcurrentHashMap: 멀티스레드 환경에서 안전하게 읽기/쓰기 가능
@Component
class GitHubRateLimitState {

    private val state = ConcurrentHashMap<Long, RateLimitInfo>()

    fun update(installationId: Long, remaining: Int, resetAt: Instant) {
        state[installationId] = RateLimitInfo(remaining, resetAt)
    }

    fun getRemainingOrNull(installationId: Long): RateLimitInfo? = state[installationId]
}
