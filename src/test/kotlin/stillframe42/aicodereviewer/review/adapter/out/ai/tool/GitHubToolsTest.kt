package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials

// GitHubTools 통합 테스트
// 실제 GitHub API 호출 — 환경변수 미설정 시 assumeTrue로 자동 스킵
@SpringBootTest
class GitHubToolsTest {

    @Autowired
    private lateinit var gitHubTools: GitHubTools

    @Test
    fun `GitHubTools 빈이 Spring 컨텍스트에 등록된다`() {
        assertThat(gitHubTools).isNotNull
    }

    // ── getFileContent ──────────────────────────────

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

    // ── getRelatedFile ──────────────────────────────

    @Test
    fun `루트 수준 파일로 관련 파일을 조회하면 같은 디렉토리 목록만 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getRelatedFile(
            repositoryFullName = repo,
            filePath = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).contains("[같은 디렉토리:")
        assertThat(result).doesNotContain("[상위 디렉토리:")
    }

    @Test
    fun `루트 수준 파일 조회 결과에 자기 자신이 포함되지 않는다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getRelatedFile(
            repositoryFullName = repo,
            filePath = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        val lines = result.lines().filter { it.startsWith("- ") }
        assertThat(lines).noneMatch { it.contains("README.md") }
    }

    @Test
    fun `하위 디렉토리 파일로 관련 파일을 조회하면 같은 디렉토리와 상위 디렉토리 목록을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getRelatedFile(
            repositoryFullName = repo,
            filePath = "gradle/wrapper/gradle-wrapper.properties",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).contains("[같은 디렉토리: gradle/wrapper]")
        assertThat(result).contains("[상위 디렉토리: gradle]")
        val lines = result.lines().filter { it.startsWith("- ") }
        assertThat(lines).noneMatch { it.contains("gradle-wrapper.properties") }
    }

    @Test
    fun `존재하지 않는 경로로 관련 파일을 조회하면 오류 메시지 문자열을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getRelatedFile(
            repositoryFullName = repo,
            filePath = "this/does/not/exist.kt",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).startsWith("디렉토리를 찾을 수 없습니다")
    }

    // ── getPRDescription ──────────────────────────────

    @Test
    fun `유효한 PR 번호로 조회하면 제목을 포함한 문자열을 반환한다`() {
        val (installationId, repo, prNumber) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getPRDescription(
            repositoryFullName = repo,
            prNumber = prNumber,
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).startsWith("제목:")
        assertThat(result).contains("설명:")
    }

    @Test
    fun `존재하지 않는 PR 번호로 조회하면 오류 메시지 문자열을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getPRDescription(
            repositoryFullName = repo,
            prNumber = Int.MAX_VALUE,
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).startsWith("PR을 찾을 수 없습니다")
    }

    // ── getFileHistory ──────────────────────────────

    @Test
    fun `존재하는 파일의 커밋 히스토리를 조회하면 번호 붙은 항목 목록을 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        val result = gitHubTools.getFileHistory(
            repositoryFullName = repo,
            filePath = "README.md",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).contains("[1]")
        assertThat(result).contains("작성자:")
    }

    @Test
    fun `존재하지 않는 파일의 커밋 히스토리를 조회하면 빈 이력 메시지를 반환한다`() {
        val (installationId, repo, _) = GitHubTestCredentials.assumeValidAndGet()

        // GitHub Commits API는 존재하지 않는 파일 경로에도 200 + 빈 배열 반환
        val result = gitHubTools.getFileHistory(
            repositoryFullName = repo,
            filePath = "this/does/not/exist.kt",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).startsWith("커밋 이력이 없습니다")
    }
}
