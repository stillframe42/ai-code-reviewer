package stillframe42.aicodereviewer.github.adapter.out.github.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GitHubRateLimitStateTest {

    private val state = GitHubRateLimitState()
    private val resetAt = Instant.parse("2026-03-30T12:00:00Z")

    @Test
    fun `상태가 없으면 null을 반환한다`() {
        assertThat(state.getRemainingOrNull(1L)).isNull()
    }

    @Test
    fun `update 후 저장된 정보를 반환한다`() {
        state.update(1L, 42, resetAt)
        assertThat(state.getRemainingOrNull(1L)).isEqualTo(RateLimitInfo(42, resetAt))
    }

    @Test
    fun `같은 installationId로 update하면 최신 값으로 덮어쓴다`() {
        state.update(1L, 100, resetAt)
        state.update(1L, 50, resetAt)
        assertThat(state.getRemainingOrNull(1L)!!.remaining).isEqualTo(50)
    }

    @Test
    fun `다른 installationId는 독립적으로 관리된다`() {
        state.update(1L, 100, resetAt)
        state.update(2L, 5, resetAt)
        assertThat(state.getRemainingOrNull(1L)!!.remaining).isEqualTo(100)
        assertThat(state.getRemainingOrNull(2L)!!.remaining).isEqualTo(5)
    }
}
