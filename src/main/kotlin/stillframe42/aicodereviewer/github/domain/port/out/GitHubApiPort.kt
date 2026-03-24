package stillframe42.aicodereviewer.github.domain.port.out

import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrReview

// GitHub API 출력 포트 — GitHub REST API 호출을 추상화하는 인터페이스
interface GitHubApiPort {
    // PR의 unified diff를 조회한다
    suspend fun getPrDiff(
        repositoryFullName: String,
        pullRequestNumber: Int,
        installationId: Long,
    ): String

    // PR의 변경 파일 목록과 메타데이터를 조회한다
    suspend fun getPrFiles(
        repositoryFullName: String,
        pullRequestNumber: Int,
        installationId: Long,
    ): List<PrFile>

    // PR에 코드 리뷰를 등록한다 (Pull Request Reviews API)
    suspend fun postPrReview(
        repositoryFullName: String,
        pullRequestNumber: Int,
        review: PrReview,
        installationId: Long,
    )
}
