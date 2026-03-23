package stillframe42.aicodereviewer.github.domain.port.out

// GitHub App Installation Access Token 발급 아웃바운드 포트
interface GitHubTokenPort {
    // installationId에 해당하는 Installation Access Token을 반환한다
    // 유효한 토큰이 캐시에 있으면 캐시에서 반환하고, 만료 5분 전이면 재발급한다
    suspend fun getInstallationToken(installationId: Long): String
}
