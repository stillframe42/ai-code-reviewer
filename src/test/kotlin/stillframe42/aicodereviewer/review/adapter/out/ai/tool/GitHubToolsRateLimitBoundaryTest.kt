package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.github.adapter.out.github.ratelimit.GitHubRateLimitState
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import java.time.Instant

// Rate Limit 차단 임계 경계(< 10) 전용 테스트 — GitHubRateLimitChecker 단위 테스트 삭제로 사라진
// remaining=10 통과 / remaining=9 차단 / 정보 없음 통과 경계 커버리지를 통합 레벨에서 복원한다
class GitHubToolsRateLimitBoundaryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var gitHubTools: GitHubTools

    @Autowired
    private lateinit var rateLimitState: GitHubRateLimitState

    private val resetAt = Instant.now().plusSeconds(600)

    @BeforeEach
    fun setUpStubs() {
        // 비차단 경로는 토큰 발급과 파일 조회까지 진행되므로 stub 필요
        WireMockStubs.stubAnyInstallationToken(wireMock)
        WireMockStubs.stubGitHubFileContent(wireMock, WireMockStubs.TEST_REPO, "README.md", "main")
    }

    @Test
    fun `Rate Limit 잔여 횟수가 10이면 차단하지 않는다`() {
        // Long.MAX_VALUE - 10 부터: 다른 테스트에서 사용하는 installationId 와 충돌 방지
        val installationId = Long.MAX_VALUE - 10
        rateLimitState.update(installationId, 10, resetAt)

        val result = gitHubTools.getFileContent(
            repositoryFullName = WireMockStubs.TEST_REPO,
            path = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).doesNotContain("Rate Limit 임박")
    }

    @Test
    fun `Rate Limit 잔여 횟수가 9이면 안내 메시지를 반환한다`() {
        val installationId = Long.MAX_VALUE - 11
        rateLimitState.update(installationId, 9, resetAt)

        val result = gitHubTools.getFileContent(
            repositoryFullName = WireMockStubs.TEST_REPO,
            path = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).contains("Rate Limit 임박")
        assertThat(result).contains("9건 남음")
    }

    @Test
    fun `Rate Limit 정보가 없으면 차단하지 않는다`() {
        // 어떤 테스트도 update 하지 않는 installationId — 첫 Tool 호출 직전(헤더 미수신) 상태 재현
        val installationId = Long.MAX_VALUE - 12

        val result = gitHubTools.getFileContent(
            repositoryFullName = WireMockStubs.TEST_REPO,
            path = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).doesNotContain("Rate Limit 임박")
    }
}
