package stillframe42.aicodereviewer.github.adapter.out.github

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.domain.port.out.GitHubApiPort
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials

// GitHubApiAdapter 통합 테스트
// 실제 GitHub App 자격증명 및 테스트 PR 정보가 필요한 환경에서만 실행된다 (없으면 자동 스킵)
// 필요 환경변수:
//   GITHUB_APP_ID          — GitHub App ID
//   GITHUB_INSTALLATION_ID — GitHub App Installation ID
//   GITHUB_TEST_REPO       — "owner/repo" 형식 테스트 레포
//   GITHUB_TEST_PR_NUMBER  — diff/comment 테스트용 PR 번호
@SpringBootTest
class GitHubApiAdapterTest {

    @Autowired
    private lateinit var gitHubApiPort: GitHubApiPort

    @Test
    fun `유효한 PR 번호로 diff를 조회하면 내용이 비어있지 않다`() = runBlocking {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeValidAndGet()

        val prDiff = gitHubApiPort.getPrDiff(
            repositoryFullName = repo,
            pullRequestNumber = prNumber,
            installationId = installationId,
        )

        assertThat(prDiff).isNotBlank()
        // unified diff 형식은 "diff --git"으로 시작한다
        assertThat(prDiff).contains("diff --git")
        Unit
    }

    @Test
    fun `PR 파일 목록을 조회하면 파일 정보가 반환된다`() = runBlocking {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeValidAndGet()

        val files = gitHubApiPort.getPrFiles(
            repositoryFullName = repo,
            pullRequestNumber = prNumber,
            installationId = installationId,
        )

        assertThat(files).isNotEmpty()
        assertThat(files).allSatisfy { file ->
            assertThat(file.filename).isNotBlank()
            assertThat(file.changes).isGreaterThanOrEqualTo(0)
        }
        Unit
    }

    @Test
    fun `유효한 PR에 리뷰를 등록하면 양수 review ID가 반환된다`() = runBlocking {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeValidAndGet()

        val reviewId = gitHubApiPort.postPrReview(
            repositoryFullName = repo,
            pullRequestNumber = prNumber,
            review = stillframe42.aicodereviewer.github.domain.model.PrReview(
                body = "[테스트] GitHubApiAdapter PR Reviews API 통합 테스트",
            ),
            installationId = installationId,
        )

        assertThat(reviewId).isPositive()
        Unit
    }

    @Test
    fun `등록된 REQUEST_CHANGES 리뷰를 dismiss하면 예외가 발생하지 않는다`() = runBlocking {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeValidAndGet()

        // REQUEST_CHANGES 타입만 dismiss 가능
        val reviewId = gitHubApiPort.postPrReview(
            repositoryFullName = repo,
            pullRequestNumber = prNumber,
            review = stillframe42.aicodereviewer.github.domain.model.PrReview(
                body = "[테스트] dismiss 테스트용 리뷰",
                event = stillframe42.aicodereviewer.github.domain.model.PrReviewEvent.REQUEST_CHANGES,
            ),
            installationId = installationId,
        )

        gitHubApiPort.dismissPrReview(
            repositoryFullName = repo,
            pullRequestNumber = prNumber,
            reviewId = reviewId,
            installationId = installationId,
        )
        Unit
    }
}
