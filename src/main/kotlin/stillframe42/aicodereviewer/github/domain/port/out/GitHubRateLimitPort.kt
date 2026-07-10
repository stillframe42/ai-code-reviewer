package stillframe42.aicodereviewer.github.domain.port.out

import stillframe42.aicodereviewer.github.domain.model.GitHubRateLimit

// GitHub API Rate Limit 조회 출력 포트 — 임계값 판단·안내 문구는 소비자 책임
interface GitHubRateLimitPort {
    // 정보 없으면 null (첫 Tool 호출 직전 — 아직 헤더 수신 전)
    fun currentRateLimit(installationId: Long): GitHubRateLimit?
}
