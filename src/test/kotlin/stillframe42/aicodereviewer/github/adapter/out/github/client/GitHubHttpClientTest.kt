package stillframe42.aicodereviewer.github.adapter.out.github.client

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.github.adapter.out.github.GitHubAppJwtGenerator
import stillframe42.aicodereviewer.github.adapter.out.github.dto.CreatePullRequestReviewRequest
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs

// GitHubHttpClient 통합 테스트 — WireMock으로 GitHub API를 모킹합니다.
// integration-test 프로파일의 테스트 RSA 키로 JWT를 생성하며 WireMock은 JWT 검증을 하지 않는다.
class GitHubHttpClientTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var gitHubHttpClient: GitHubHttpClient

    @Autowired
    private lateinit var jwtGenerator: GitHubAppJwtGenerator

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubInstallationToken(wireMock, WireMockStubs.TEST_INSTALLATION_ID)
        WireMockStubs.stubPrDiff(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, AnthropicResponseFixtures.SIMPLE_DIFF)
        WireMockStubs.stubPostPrReview(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, reviewId = 9001L)
        WireMockStubs.stubDismissPrReview(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, reviewId = 9001L)
        WireMockStubs.stubGitHubDirectoryContents(
            wireMock,
            WireMockStubs.TEST_REPO,
            path = "",
            ref = "main",
            entries = """[{"name":"README.md","path":"README.md","type":"file"},{"name":"src","path":"src","type":"dir"}]""",
        )
    }

    @Test
    fun `유효한 Installation ID로 토큰을 발급받는다`() = runBlocking {
        val jwt = jwtGenerator.generate()
        val response = gitHubHttpClient.fetchInstallationToken(WireMockStubs.TEST_INSTALLATION_ID, jwt)

        // WireMock stub이 "ghs_test_token"을 반환한다
        assertThat(response.token).startsWith("ghs_")
        assertThat(response.expiresAt).isNotBlank()
        Unit
    }

    @Test
    fun `유효한 PR 번호로 diff를 조회하면 diff --git을 포함한다`() = runBlocking {
        val jwt = jwtGenerator.generate()
        val tokenResponse = gitHubHttpClient.fetchInstallationToken(WireMockStubs.TEST_INSTALLATION_ID, jwt)

        val diff = gitHubHttpClient.fetchPrDiff(WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, tokenResponse.token)

        assertThat(diff).isNotBlank()
        assertThat(diff).contains("diff --git")
        Unit
    }

    @Test
    fun `유효한 PR에 리뷰를 등록하면 양수 review ID가 반환된다`() = runBlocking {
        val jwt = jwtGenerator.generate()
        val tokenResponse = gitHubHttpClient.fetchInstallationToken(WireMockStubs.TEST_INSTALLATION_ID, jwt)

        val reviewId = gitHubHttpClient.postPrReview(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            request = CreatePullRequestReviewRequest(
                body = "[테스트] GitHubHttpClient PR Reviews API 통합 테스트",
                event = "COMMENT",
            ),
            token = tokenResponse.token,
        )

        assertThat(reviewId).isPositive()
        Unit
    }

    @Test
    fun `등록된 리뷰를 dismiss하면 예외가 발생하지 않는다`() = runBlocking {
        val jwt = jwtGenerator.generate()
        val tokenResponse = gitHubHttpClient.fetchInstallationToken(WireMockStubs.TEST_INSTALLATION_ID, jwt)

        // REQUEST_CHANGES 타입만 dismiss 가능 — stub은 reviewId=9001L을 반환
        val reviewId = gitHubHttpClient.postPrReview(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            request = CreatePullRequestReviewRequest(
                body = "[테스트] dismiss 테스트용 리뷰",
                event = "REQUEST_CHANGES",
            ),
            token = tokenResponse.token,
        )

        gitHubHttpClient.dismissPrReview(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            reviewId = reviewId,
            token = tokenResponse.token,
        )
        Unit
    }

    @Test
    fun `디렉토리 경로를 조회하면 항목 목록을 반환한다`() = runBlocking {
        val jwt = jwtGenerator.generate()
        val tokenResponse = gitHubHttpClient.fetchInstallationToken(WireMockStubs.TEST_INSTALLATION_ID, jwt)

        val entries = gitHubHttpClient.fetchDirectoryContents(
            repositoryFullName = WireMockStubs.TEST_REPO,
            path = "",  // 루트 디렉토리
            ref = "main",
            token = tokenResponse.token,
            installationId = WireMockStubs.TEST_INSTALLATION_ID,
        )

        assertThat(entries).isNotEmpty()
        assertThat(entries).allMatch { it.name.isNotBlank() }
        assertThat(entries).allMatch { it.path.isNotBlank() }
        assertThat(entries).allMatch { it.type in listOf("file", "dir", "symlink") }
        Unit
    }
}
