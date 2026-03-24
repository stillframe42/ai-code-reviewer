package stillframe42.aicodereviewer.github.domain.port.out

// GitHub API 출력 포트 — GitHub REST API 호출을 추상화하는 인터페이스
interface GitHubApiPort {
    // PR의 unified diff를 조회한다
    suspend fun getPrDiff(
        repositoryFullName: String,
        pullRequestNumber: Int,
        installationId: Long,
    ): String

    // PR에 코드 리뷰 코멘트를 등록한다
    suspend fun postReviewComment(
        repositoryFullName: String,
        pullRequestNumber: Int,
        comment: String,
        installationId: Long,
    )
}
