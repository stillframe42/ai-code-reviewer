package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials

// GitHubTools 통합 테스트
@SpringBootTest
class GitHubToolsTest {

    @Autowired
    private lateinit var gitHubTools: GitHubTools

    @Test
    fun `GitHubTools 빈이 Spring 컨텍스트에 등록된다`() {
        assertThat(gitHubTools).isNotNull
    }

    @Test
    fun `존재하는 파일을 조회하면 파일 내용을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getFileContent(
            repositoryFullName = repo,
            path = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).isNotBlank()
        assertThat(result).doesNotStartWith("파일을 찾을 수 없습니다")
        assertThat(result).doesNotStartWith("GitHub API 오류")
    }

    @Test
    fun `존재하지 않는 파일을 조회하면 오류 메시지 문자열을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getFileContent(
            repositoryFullName = repo,
            path = "this/file/does/not/exist.kt",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).startsWith("파일을 찾을 수 없습니다")
    }
}
