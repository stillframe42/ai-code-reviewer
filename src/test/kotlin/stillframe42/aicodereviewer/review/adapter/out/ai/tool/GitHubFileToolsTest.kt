package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials

// GitHubFileTools 통합 테스트
@SpringBootTest
class GitHubFileToolsTest {

    @Autowired
    private lateinit var gitHubFileTools: GitHubFileTools

    @Test
    fun `GitHubFileTools 빈이 Spring 컨텍스트에 등록된다`() {
        assertThat(gitHubFileTools).isNotNull
    }

    @Test
    fun `존재하는 파일을 조회하면 파일 내용을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubFileTools.getFileContent(
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

        val result = gitHubFileTools.getFileContent(
            repositoryFullName = repo,
            path = "this/file/does/not/exist.kt",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).startsWith("파일을 찾을 수 없습니다")
    }

    @Test
    fun `존재하는 파일의 관련 파일을 조회하면 디렉토리 목록을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubFileTools.getRelatedFile(
            repositoryFullName = repo,
            filePath = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).isNotBlank()
        assertThat(result).doesNotStartWith("디렉토리를 찾을 수 없습니다")
        assertThat(result).doesNotStartWith("GitHub API 오류")
    }

    @Test
    fun `존재하지 않는 파일의 관련 파일을 조회하면 오류 메시지 문자열을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubFileTools.getRelatedFile(
            repositoryFullName = repo,
            filePath = "this/path/does/not/exist.kt",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).startsWith("디렉토리를 찾을 수 없습니다")
    }
}
