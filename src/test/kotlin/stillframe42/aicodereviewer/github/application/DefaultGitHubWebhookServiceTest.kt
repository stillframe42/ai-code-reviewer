package stillframe42.aicodereviewer.github.application

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.domain.model.PullRequestAction
import stillframe42.aicodereviewer.github.domain.model.PullRequestEvent
import stillframe42.aicodereviewer.github.domain.port.out.ReviewCommentFormatterPort
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// DefaultGitHubWebhookService 통합 테스트
// 실제 GitHub App 자격증명 + Anthropic API 키가 필요한 테스트는 자동 스킵됩니다.
// 필요 환경변수:
//   GITHUB_APP_ID          — GitHub App ID
//   GITHUB_INSTALLATION_ID — GitHub App Installation ID
//   GITHUB_TEST_REPO       — "owner/repo" 형식 테스트 레포
//   GITHUB_TEST_PR_NUMBER  — 테스트용 PR 번호
//   ANTHROPIC_API_KEY      — 실제 Anthropic API 키
@SpringBootTest
class DefaultGitHubWebhookServiceTest {

    @Autowired
    private lateinit var service: DefaultGitHubWebhookService

    // 포맷팅 단위 검증을 위해 포매터 포트를 직접 주입
    @Autowired
    private lateinit var formatter: ReviewCommentFormatterPort

    // ── 통합 테스트 (실제 API 호출) ──────────────────────────────────────────

    @Test
    fun `OPENED 이벤트를 처리하면 PR에 리뷰 코멘트가 등록된다`() = runBlocking {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeFullCredentials()

        // 예외 없이 완료되면 성공 (실제 PR에 코멘트가 등록됨)
        service.handlePullRequestEvent(
            PullRequestEvent(
                action = PullRequestAction.OPENED,
                installationId = installationId,
                repositoryFullName = repo,
                pullRequestNumber = prNumber,
                headSha = "HEAD",
            ),
        )
        Unit
    }

    @Test
    fun `SYNCHRONIZE 이벤트를 처리하면 예외가 발생하지 않는다`() = runBlocking {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeFullCredentials()

        service.handlePullRequestEvent(
            PullRequestEvent(
                action = PullRequestAction.SYNCHRONIZE,
                installationId = installationId,
                repositoryFullName = repo,
                pullRequestNumber = prNumber,
                headSha = "HEAD",
            ),
        )
        Unit
    }

    // ── MarkdownReviewCommentFormatter 단위 검증 ──────────────────────────────

    @Test
    fun `이슈와 잘한 점이 있는 리뷰를 포맷하면 모든 섹션이 포함된다`() {
        val review = CodeReview(
            overallScore =7,
            summary = "전반적으로 읽기 쉽게 작성된 코드입니다.",
            issues = listOf(
                CodeIssue(
                    id = "1",
                    category = IssueCategory.SECURITY,
                    line = 42,
                    severity = IssueSeverity.CRITICAL,
                    description = "SQL Injection 취약점",
                    suggestion = "PreparedStatement를 사용하세요",
                ),
            ),
            positives = listOf("명확한 변수명 사용"),
        )

        val result = formatter.format(review)

        assertThat(result).contains("종합 점수: 7/10")
        assertThat(result).contains("전반적으로 읽기 쉽게 작성된 코드입니다.")
        assertThat(result).contains("이슈 목록 (1건)")
        assertThat(result).contains("CRITICAL")
        assertThat(result).contains("SECURITY")
        assertThat(result).contains("SQL Injection 취약점")
        assertThat(result).contains("42번째 줄")
        assertThat(result).contains("PreparedStatement를 사용하세요")
        assertThat(result).contains("잘한 점")
        assertThat(result).contains("명확한 변수명 사용")
        assertThat(result).contains("AI가 자동으로 생성했습니다")
    }

    @Test
    fun `이슈가 없는 리뷰를 포맷하면 이슈 없음 메시지가 포함된다`() {
        val review = CodeReview(
            overallScore =10,
            summary = "완벽한 코드입니다.",
            issues = emptyList(),
            positives = listOf("깔끔한 구조"),
        )

        val result = formatter.format(review)

        assertThat(result).contains("이슈 목록 (0건)")
        assertThat(result).contains("발견된 이슈가 없습니다.")
    }

    @Test
    fun `잘한 점이 없는 리뷰를 포맷하면 잘한 점 섹션이 생략된다`() {
        val review = CodeReview(
            overallScore =3,
            summary = "개선이 필요한 코드입니다.",
            issues = listOf(
                CodeIssue(
                    id = "1",
                    category = IssueCategory.PERFORMANCE,
                    line = null,
                    severity = IssueSeverity.MAJOR,
                    description = "N+1 쿼리 발생",
                    suggestion = "fetch join을 사용하세요",
                ),
            ),
            positives = emptyList(),
        )

        val result = formatter.format(review)

        assertThat(result).doesNotContain("잘한 점")
    }

    @Test
    fun `line이 null인 이슈를 포맷하면 라인 항목이 생략된다`() {
        val review = CodeReview(
            overallScore =5,
            summary = "보통 수준의 코드입니다.",
            issues = listOf(
                CodeIssue(
                    id = "1",
                    category = IssueCategory.ARCHITECTURE,
                    line = null,
                    severity = IssueSeverity.MINOR,
                    description = "SRP 위반 의심",
                    suggestion = "단일 책임 원칙을 따르도록 클래스를 분리하세요",
                ),
            ),
            positives = emptyList(),
        )

        val result = formatter.format(review)

        assertThat(result).contains("SRP 위반 의심")
        assertThat(result).doesNotContain("번째 줄")
    }
}
