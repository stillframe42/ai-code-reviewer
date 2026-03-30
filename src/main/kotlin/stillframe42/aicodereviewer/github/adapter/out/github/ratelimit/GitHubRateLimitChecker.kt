package stillframe42.aicodereviewer.github.adapter.out.github.ratelimit

import org.springframework.stereotype.Component

// remaining < 10이면 LLM에 반환할 안내 메시지를 생성한다, 아니면 null을 반환한다
// Rate Limit 정보가 없으면 null을 반환한다 (첫 Tool 호출 직전 — 아직 헤더 수신 전)
@Component
class GitHubRateLimitChecker(
    private val rateLimitState: GitHubRateLimitState,
) {

    fun checkOrNull(installationId: Long): String? {
        val info = rateLimitState.getRemainingOrNull(installationId) ?: return null
        return if (info.remaining < 10) {
            "GitHub API Rate Limit 임박: ${info.remaining}건 남음, ${info.resetAt} 초기화 예정"
        } else null
    }
}
