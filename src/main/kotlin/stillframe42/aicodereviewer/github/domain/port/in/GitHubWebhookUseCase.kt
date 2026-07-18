package stillframe42.aicodereviewer.github.domain.port.`in`

import stillframe42.aicodereviewer.github.domain.model.PullRequestEvent

// GitHub Webhook 처리 입력 포트 — Webhook 수신 시 호출되는 유스케이스 인터페이스
interface GitHubWebhookUseCase {
    fun handlePullRequestEvent(event: PullRequestEvent)
}
