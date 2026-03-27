package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

// GitHubTools 통합 테스트
// Phase 2: 빈 등록 및 기반 구조 검증
@SpringBootTest
class GitHubToolsTest {

    @Autowired
    private lateinit var gitHubTools: GitHubTools

    @Test
    fun `GitHubTools 빈이 Spring 컨텍스트에 등록된다`() {
        assertThat(gitHubTools).isNotNull
    }

    // Phase 3 구현 후 실제 API 호출 테스트로 교체
    @Test
    fun `getFileContent는 Phase 3 구현 전 NotImplementedError를 던진다`() {
        assertThrows<NotImplementedError> {
            gitHubTools.getFileContent(
                owner = "test-owner",
                repo = "test-repo",
                path = "README.md",
                ref = "main",
                installationId = 0L,
            )
        }
    }
}
