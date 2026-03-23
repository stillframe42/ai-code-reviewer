package stillframe42.aicodereviewer.github.domain.model

// GitHub Webhook에서 수신하는 PR 이벤트 타입
enum class PullRequestAction {
    OPENED,       // 새로운 PR 생성
    SYNCHRONIZE,  // PR에 새로운 커밋 푸시
    REOPENED,     // 닫힌 PR 다시 열기
}
