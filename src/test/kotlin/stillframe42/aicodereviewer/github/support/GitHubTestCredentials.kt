package stillframe42.aicodereviewer.github.support

import org.junit.jupiter.api.Assumptions.assumeTrue

// GitHub 통합 테스트용 환경변수 검증 공통 유틸
// 필요한 환경변수가 없으면 assumeTrue가 테스트를 자동 스킵한다
object GitHubTestCredentials {

    data class Credentials(val installationId: Long, val repo: String, val prNumber: Int)

    // GitHub App 자격증명 + 테스트 PR 정보 검증 후 반환
    // 필요 환경변수: GITHUB_APP_ID, GITHUB_INSTALLATION_ID, GITHUB_TEST_REPO, GITHUB_TEST_PR_NUMBER
    fun assumeValidAndGet(): Credentials {
        val appId = System.getenv("GITHUB_APP_ID")
        val installationId = System.getenv("GITHUB_INSTALLATION_ID")?.toLongOrNull()
        val testRepo = System.getenv("GITHUB_TEST_REPO")
        val testPrNumber = System.getenv("GITHUB_TEST_PR_NUMBER")?.toIntOrNull()
        assumeTrue(
            appId != null && appId != "0"
                && installationId != null
                && testRepo != null
                && testPrNumber != null,
            "실제 GitHub App 자격증명 및 테스트 PR 정보가 설정된 환경에서만 실행됩니다",
        )
        return Credentials(installationId!!, testRepo!!, testPrNumber!!)
    }

    // Installation Token 발급 테스트에 필요한 최소 자격증명만 검증 후 installationId 반환
    // 필요 환경변수: GITHUB_APP_ID, GITHUB_INSTALLATION_ID
    fun assumeTokenCredentials(): Long {
        val appId = System.getenv("GITHUB_APP_ID")
        val installationId = System.getenv("GITHUB_INSTALLATION_ID")?.toLongOrNull()
        assumeTrue(
            appId != null && appId != "0" && installationId != null,
            "실제 GITHUB_APP_ID, GITHUB_INSTALLATION_ID가 설정된 환경에서만 실행됩니다",
        )
        return installationId!!
    }

    // GitHub + Anthropic 자격증명이 모두 필요한 full 통합 테스트용 검증
    // 필요 환경변수: 위 4개 + ANTHROPIC_API_KEY
    fun assumeFullCredentials(): Credentials {
        val anthropicKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            anthropicKey != null && anthropicKey.isNotBlank() && anthropicKey != "test-dummy-key",
            "실제 GitHub App 자격증명 및 Anthropic API 키가 설정된 환경에서만 실행됩니다",
        )
        return assumeValidAndGet()
    }
}
