package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.github.adapter.out.github.ratelimit.GitHubRateLimitState
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials
import java.time.Instant

// GitHubTools 통합 테스트
// 실제 GitHub API 호출 — 환경변수 미설정 시 assumeTrue로 자동 스킵
@SpringBootTest
class GitHubToolsTest {

    @Autowired
    private lateinit var gitHubTools: GitHubTools

    @Autowired
    private lateinit var rateLimitState: GitHubRateLimitState

    @BeforeEach
    fun resetRateLimitState() {
        // Rate Limit 테스트용으로 등록된 더미 installationId의 상태를 초기화한다
        // 카운터 테스트가 Long.MAX_VALUE를 재사용하므로 Rate Limit 간섭을 방지한다
        rateLimitState.update(Long.MAX_VALUE, Int.MAX_VALUE, Instant.now().plusSeconds(600))
    }

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
        assertThat(lines).noneMatch { it == "- README.md" }
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
        assertThat(result).doesNotStartWith("커밋 이력이 없습니다")
        assertThat(result).doesNotStartWith("파일을 찾을 수 없습니다")
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

    // ── Rate Limit 차단 ──────────────────────────────

    @Test
    fun `Rate Limit 잔여 횟수가 9 이하이면 getFileContent 호출 시 안내 메시지를 반환한다`() {
        // Long.MAX_VALUE: 실제 테스트에서 사용하는 installationId와 충돌하지 않는 더미 값
        val installationId = Long.MAX_VALUE
        rateLimitState.update(installationId, 5, Instant.now().plusSeconds(600))

        val result = gitHubTools.getFileContent(
            repositoryFullName = "owner/repo",
            path = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to installationId)),
        )

        assertThat(result).contains("Rate Limit 임박")
        assertThat(result).contains("5건 남음")
    }

    // ── Tool 호출 횟수 제한 ──────────────────────────────

    @Test
    fun `Tool 호출 카운터가 최대 횟수를 초과하면 에러 문자열을 반환한다`() {
        val counter = java.util.concurrent.atomic.AtomicInteger(5)  // 이미 5회 소진
        val result = gitHubTools.getFileContent(
            repositoryFullName = "owner/repo",
            path = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf(
                "installationId" to Long.MAX_VALUE,  // 실제 API 호출 없이 카운터만 검사
                "toolCallCounter" to counter,
            )),
        )
        assertThat(result).contains("Tool 호출 한도")
        assertThat(result).contains("5회")
    }

    @Test
    fun `Tool 호출 카운터가 없으면 횟수 제한 없이 정상 실행 흐름을 밟는다`() {
        val rateLimitDummyId = Long.MAX_VALUE - 1  // rateLimitState에 등록되지 않은 ID
        val result = gitHubTools.getFileContent(
            repositoryFullName = "owner/repo",
            path = "README.md",
            ref = "main",
            toolContext = ToolContext(mapOf("installationId" to rateLimitDummyId)),
        )
        // counter가 없으면 카운터 제한 에러는 발생하지 않는다
        assertThat(result).doesNotContain("Tool 호출 한도")
    }
}
