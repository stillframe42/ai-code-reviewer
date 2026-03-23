package stillframe42.aicodereviewer.github.infrastructure

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.adapter.out.github.GitHubAppJwtGenerator

// GitHubHttpClient 통합 테스트
// 실제 GitHub App 자격증명 및 테스트 PR 정보가 필요한 환경에서만 실행된다 (없으면 자동 스킵)
// 필요 환경변수:
//   GITHUB_APP_ID          — GitHub App ID
//   GITHUB_INSTALLATION_ID — GitHub App Installation ID
//   GITHUB_TEST_REPO       — "owner/repo" 형식 테스트 레포
//   GITHUB_TEST_PR_NUMBER  — diff/comment 테스트용 PR 번호
@SpringBootTest
class GitHubHttpClientTest {

    @Autowired
    private lateinit var gitHubHttpClient: GitHubHttpClient

    @Autowired
    private lateinit var jwtGenerator: GitHubAppJwtGenerator

    private fun assumeGitHubCredentials(): Triple<Long, String, Int> {
        val appId = System.getenv("GITHUB_APP_ID")
        val installationId = System.getenv("GITHUB_INSTALLATION_ID")?.toLongOrNull()
        val testRepo = System.getenv("GITHUB_TEST_REPO")
        val testPrNumber = System.getenv("GITHUB_TEST_PR_NUMBER")?.toIntOrNull()
        assumeTrue(
            appId != null && appId != "0"
                && installationId != null
                && testRepo != null
                && testPrNumber != null,
            "실제 GitHub App 자격증명 및 테스트 PR 정보가 설정된 환경에서만 실행됩니다",
        )
        return Triple(installationId!!, testRepo!!, testPrNumber!!)
    }

    @Test
    fun `유효한 Installation ID로 토큰을 발급받는다`() = runBlocking {
        val (installationId, _, _) = assumeGitHubCredentials()

        val jwt = jwtGenerator.generate()
        val response = gitHubHttpClient.fetchInstallationToken(installationId, jwt)

        // GitHub Installation Access Token은 "ghs_" 접두사를 가진다
        assertThat(response.token).startsWith("ghs_")
        assertThat(response.expiresAt).isNotBlank()
        Unit
    }

    @Test
    fun `유효한 PR 번호로 diff를 조회하면 diff --git을 포함한다`() = runBlocking {
        val (installationId, repo, prNumber) = assumeGitHubCredentials()

        val jwt = jwtGenerator.generate()
        val tokenResponse = gitHubHttpClient.fetchInstallationToken(installationId, jwt)

        val diff = gitHubHttpClient.fetchPrDiff(repo, prNumber, tokenResponse.token)

        assertThat(diff).isNotBlank()
        assertThat(diff).contains("diff --git")
        Unit
    }

    @Test
    fun `유효한 PR에 코멘트를 등록하면 예외가 발생하지 않는다`() = runBlocking {
        val (installationId, repo, prNumber) = assumeGitHubCredentials()

        val jwt = jwtGenerator.generate()
        val tokenResponse = gitHubHttpClient.fetchInstallationToken(installationId, jwt)

        // 예외 없이 완료되면 성공
        gitHubHttpClient.postIssueComment(repo, prNumber, "[테스트] GitHubHttpClient 통합 테스트 자동 코멘트", tokenResponse.token)
        Unit
    }
}
