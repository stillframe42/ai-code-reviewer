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
    fun `유효한 PR에 코멘트를 등록하면 예외가 발생하지 않는다`() = runBlocking {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeValidAndGet()

        // 예외 없이 완료되면 성공 (postReviewComment는 Unit 반환)
        gitHubApiPort.postReviewComment(
            repositoryFullName = repo,
            pullRequestNumber = prNumber,
            comment = "[테스트] GitHubApiAdapter 통합 테스트 자동 코멘트",
            installationId = installationId,
        )
        Unit
    }
}
