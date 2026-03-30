package stillframe42.aicodereviewer.github.adapter.out.github.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GitHubRateLimitCheckerTest {

    private val state = GitHubRateLimitState()
    private val checker = GitHubRateLimitChecker(state)
    private val resetAt = Instant.parse("2026-03-30T12:00:00Z")

    @Test
    fun `Rate Limit 정보가 없으면 null을 반환한다`() {
        assertThat(checker.checkOrNull(1L)).isNull()
    }

    @Test
    fun `remaining이 10이면 null을 반환한다`() {
        state.update(1L, 10, resetAt)
        assertThat(checker.checkOrNull(1L)).isNull()
    }

    @Test
    fun `remaining이 9이면 안내 메시지를 반환한다`() {
        state.update(1L, 9, resetAt)
        val message = checker.checkOrNull(1L)
        assertThat(message).isNotNull()
        assertThat(message).contains("Rate Limit 임박")
        assertThat(message).contains("9건 남음")
    }

    @Test
    fun `remaining이 0이면 안내 메시지를 반환한다`() {
        state.update(1L, 0, resetAt)
        val message = checker.checkOrNull(1L)
        assertThat(message).isNotNull()
        assertThat(message).contains("0건 남음")
    }
}
