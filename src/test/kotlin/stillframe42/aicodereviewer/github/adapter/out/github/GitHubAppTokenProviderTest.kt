package stillframe42.aicodereviewer.github.adapter.out.github

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.adapter.out.github.GitHubAppTokenProvider.CachedToken
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials
import java.time.Instant

// GitHubAppTokenProvider 테스트
// 그룹 A: 캐싱 로직 단위 테스트 (GitHub API 불필요)
// 그룹 B: @SpringBootTest 통합 테스트 (실제 GitHub App 자격증명 필요, 없으면 자동 스킵)
@SpringBootTest
class GitHubAppTokenProviderTest {

    @Autowired
    private lateinit var tokenProvider: GitHubTokenPort

    // ─── 그룹 A: 캐싱 로직 단위 테스트 ────────────────────────────────────────

    @Test
    fun `CachedToken이 만료 4분 전이면 isExpiredOrExpiringSoon이 true를 반환한다`() {
        val token = CachedToken(
            token = "test-token",
            expiresAt = Instant.now().plusSeconds(240),  // 4분 후 만료 (5분 미만)
        )
        assertThat(token.isExpiredOrExpiringSoon()).isTrue()
    }

    @Test
    fun `CachedToken이 만료 10분 전이면 isExpiredOrExpiringSoon이 false를 반환한다`() {
        val token = CachedToken(
            token = "test-token",
            expiresAt = Instant.now().plusSeconds(600),  // 10분 후 만료 (5분 초과)
        )
        assertThat(token.isExpiredOrExpiringSoon()).isFalse()
    }

    @Test
    fun `CachedToken이 이미 만료되었으면 isExpiredOrExpiringSoon이 true를 반환한다`() {
        val token = CachedToken(
            token = "test-token",
            expiresAt = Instant.now().minusSeconds(1),  // 이미 만료
        )
        assertThat(token.isExpiredOrExpiringSoon()).isTrue()
    }

    // ─── 그룹 B: 통합 테스트 (실제 GitHub App 자격증명 필요) ───────────────────

    @Test
    fun `유효한 Installation ID로 Access Token을 발급받는다`() = runBlocking {
        val installationId = GitHubTestCredentials.assumeTokenCredentials()

        val token = tokenProvider.getInstallationToken(installationId)

        assertThat(token).isNotBlank()
        // GitHub Installation Access Token은 "ghs_" 접두사를 가진다
        assertThat(token).startsWith("ghs_")
        Unit
    }

    @Test
    fun `동일한 Installation ID로 두 번 요청하면 캐시된 동일 토큰을 반환한다`() = runBlocking {
        val installationId = GitHubTestCredentials.assumeTokenCredentials()

        val token1 = tokenProvider.getInstallationToken(installationId)
        val token2 = tokenProvider.getInstallationToken(installationId)

        // 두 번째 호출은 캐시에서 반환되므로 동일한 토큰이어야 한다
        assertThat(token1).isEqualTo(token2)
        Unit
    }
}
