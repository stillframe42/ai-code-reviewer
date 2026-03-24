package stillframe42.aicodereviewer.github.domain.model

// Webhook 페이로드에서 추출한 PR 이벤트 도메인 모델
data class PullRequestEvent(
    val action: PullRequestAction,       // PR 이벤트 타입
    val installationId: Long,            // GitHub App 설치 ID (토큰 발급용)
    val repositoryFullName: String,      // "owner/repo" 형식
    val pullRequestNumber: Int,          // PR 번호
    val headSha: String,                 // 최신 커밋 SHA (diff 조회용)
    val title: String,                   // PR 제목
    val author: String,                  // PR 작성자 GitHub 로그인명
)
