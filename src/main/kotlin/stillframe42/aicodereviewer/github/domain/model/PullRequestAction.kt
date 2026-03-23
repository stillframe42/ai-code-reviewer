package stillframe42.aicodereviewer.github.domain.model

// GitHub Webhook에서 수신하는 PR 이벤트 타입
enum class PullRequestAction {
    OPENED,       // 새로운 PR 생성
    SYNCHRONIZE,  // PR에 새로운 커밋 푸시
    REOPENED,     // 닫힌 PR 다시 열기
    ;

    companion object {
        // GitHub 페이로드의 소문자 action 문자열을 enum으로 변환한다 (지원하지 않는 action은 null 반환)
        fun fromString(value: String): PullRequestAction? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
