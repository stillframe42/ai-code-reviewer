package stillframe42.aicodereviewer.github.adapter.out.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.github.domain.model.PrReview
import stillframe42.aicodereviewer.github.domain.model.PrReviewEvent
import stillframe42.aicodereviewer.github.domain.port.out.GitHubApiPort
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.AnthropicResponseFixtures
import stillframe42.aicodereviewer.integration.support.WireMockStubs

// GitHubApiAdapter 통합 테스트 — WireMock으로 GitHub API를 모킹합니다.
class GitHubApiAdapterTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var gitHubApiPort: GitHubApiPort

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubInstallationToken(wireMock, WireMockStubs.TEST_INSTALLATION_ID)
        WireMockStubs.stubPrDiff(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, AnthropicResponseFixtures.SIMPLE_DIFF)
        WireMockStubs.stubPrFiles(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER)
        WireMockStubs.stubPostPrReview(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, reviewId = 9001L)
        WireMockStubs.stubDismissPrReview(wireMock, WireMockStubs.TEST_REPO, WireMockStubs.TEST_PR_NUMBER, reviewId = 9001L)
    }

    @Test
    fun `유효한 PR 번호로 diff를 조회하면 내용이 비어있지 않다`() {
        val prDiff = gitHubApiPort.getPrDiff(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            installationId = WireMockStubs.TEST_INSTALLATION_ID,
        )

        assertThat(prDiff).isNotBlank()
        // unified diff 형식은 "diff --git"으로 시작한다
        assertThat(prDiff).contains("diff --git")
    }

    @Test
    fun `PR 파일 목록을 조회하면 파일 정보가 반환된다`() {
        val files = gitHubApiPort.getPrFiles(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            installationId = WireMockStubs.TEST_INSTALLATION_ID,
        )

        assertThat(files).isNotEmpty()
        assertThat(files).allSatisfy { file ->
            assertThat(file.filename).isNotBlank()
            assertThat(file.changes).isGreaterThanOrEqualTo(0)
        }
    }

    @Test
    fun `유효한 PR에 리뷰를 등록하면 양수 review ID가 반환된다`() {
        val reviewId = gitHubApiPort.postPrReview(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            review = PrReview(body = "[테스트] GitHubApiAdapter PR Reviews API 통합 테스트"),
            installationId = WireMockStubs.TEST_INSTALLATION_ID,
        )

        assertThat(reviewId).isPositive()
    }

    @Test
    fun `등록된 REQUEST_CHANGES 리뷰를 dismiss하면 예외가 발생하지 않는다`() {
        // REQUEST_CHANGES 타입만 dismiss 가능 — stub은 @BeforeEach에서 reviewId=9001L로 등록됨
        val reviewId = gitHubApiPort.postPrReview(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            review = PrReview(
                body = "[테스트] dismiss 테스트용 리뷰",
                event = PrReviewEvent.REQUEST_CHANGES,
            ),
            installationId = WireMockStubs.TEST_INSTALLATION_ID,
        )

        gitHubApiPort.dismissPrReview(
            repositoryFullName = WireMockStubs.TEST_REPO,
            pullRequestNumber = WireMockStubs.TEST_PR_NUMBER,
            reviewId = reviewId,
            installationId = WireMockStubs.TEST_INSTALLATION_ID,
        )
    }
}
