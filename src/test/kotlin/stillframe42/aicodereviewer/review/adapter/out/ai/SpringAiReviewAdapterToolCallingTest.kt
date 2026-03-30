package stillframe42.aicodereviewer.review.adapter.out.ai

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials
import stillframe42.aicodereviewer.review.domain.model.ReviewMode

// Tool Calling 활성화 상태에서 SpringAiReviewAdapter 전체 동작 확인 통합 테스트
// 실제 GitHub App + Anthropic API 키가 필요 — 미설정 시 자동 스킵
@SpringBootTest
class SpringAiReviewAdapterToolCallingTest {

    @Autowired
    private lateinit var springAiReviewAdapter: SpringAiReviewAdapter

    @Test
    fun `WithGitHubTools 모드로 코드 리뷰 요청 시 CodeReview 결과를 반환한다`() {
        runBlocking {
            val (installationId, _, _) = GitHubTestCredentials.assumeFullCredentials()

            val diff = """
                diff --git a/README.md b/README.md
                index 1234567..89abcde 100644
                --- a/README.md
                +++ b/README.md
                @@ -1,3 +1,4 @@
                 # AI Code Reviewer
                +
                +GitHub Pull Request를 자동으로 리뷰하는 Spring AI 기반 서비스입니다.
            """.trimIndent()

            val result = springAiReviewAdapter.reviewCode(
                code = diff,
                provider = AiProvider.ANTHROPIC,
                mode = ReviewMode.WithGitHubTools(installationId = installationId),
            )

            assertThat(result).isNotNull()
            assertThat(result.summary).isNotBlank()
            assertThat(result.overallScore).isBetween(0, 10)
        }
    }
}
