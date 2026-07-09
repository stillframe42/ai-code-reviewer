package stillframe42.aicodereviewer.github.adapter.out.github

import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.config.GitHubProperties

// app-id fail-fast 단위 테스트 — JWT 생성·클레임 검증은 GitHubAppJwtGeneratorTest(IT)가 담당
class GitHubAppJwtGeneratorUnitTest {

    @Test
    fun `app-id가 0이면 PEM 로드 전에 명확한 메시지로 실패한다`() {
        val generator = GitHubAppJwtGenerator(
            rsaKeyLoader = RsaKeyLoader(),
            jwtSigner = JwtSigner(),
            properties = GitHubProperties(
                app = GitHubProperties.AppProperties(
                    // 존재하지 않는 경로 — fail-fast 가 PEM 로드보다 먼저면 이 경로는 접근되지 않는다
                    privateKeyPath = "nonexistent.pem",
                    appId = 0,
                    webhookSecret = "",
                ),
            ),
        )

        assertThatIllegalStateException()
            .isThrownBy { generator.generate() }
            .withMessageContaining("github.app.app-id")
    }
}
