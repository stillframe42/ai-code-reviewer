package stillframe42.aicodereviewer.github.adapter.out.github

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.adapter.out.github.dto.CreatePullRequestReviewRequest
import stillframe42.aicodereviewer.github.adapter.out.github.dto.ReviewLineComment
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrReview
import stillframe42.aicodereviewer.github.domain.port.out.GitHubApiPort
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort
import stillframe42.aicodereviewer.github.infrastructure.GitHubHttpClient

// GitHub REST API 아웃바운드 어댑터
@Component
class GitHubApiAdapter(
    private val gitHubHttpClient: GitHubHttpClient,
    private val tokenPort: GitHubTokenPort,
) : GitHubApiPort {

    // PR의 unified diff를 조회한다
    override suspend fun getPrDiff(
        repositoryFullName: String,
        pullRequestNumber: Int,
        installationId: Long,
    ): String {
        val token = tokenPort.getInstallationToken(installationId)
        return gitHubHttpClient.fetchPrDiff(repositoryFullName, pullRequestNumber, token)
    }

    // PR의 변경 파일 목록과 메타데이터를 조회한다
    override suspend fun getPrFiles(
        repositoryFullName: String,
        pullRequestNumber: Int,
        installationId: Long,
    ): List<PrFile> {
        val token = tokenPort.getInstallationToken(installationId)
        return gitHubHttpClient.fetchPrFiles(repositoryFullName, pullRequestNumber, token)
            .map { it.toDomain() }
    }

    // PR Reviews API로 코드 리뷰를 등록하고 생성된 review ID를 반환한다
    override suspend fun postPrReview(
        repositoryFullName: String,
        pullRequestNumber: Int,
        review: PrReview,
        installationId: Long,
    ): Long {
        val token = tokenPort.getInstallationToken(installationId)
        val request = CreatePullRequestReviewRequest(
            body = review.body,
            event = review.event.name,
            comments = review.lineComments.map { ReviewLineComment(path = it.path, position = it.position, body = it.body) },
        )
        return gitHubHttpClient.postPrReview(repositoryFullName, pullRequestNumber, request, token)
    }

    // 기존 리뷰를 dismiss한다 — 새 커밋 push 시 이전 리뷰 무효화에 사용
    override suspend fun dismissPrReview(
        repositoryFullName: String,
        pullRequestNumber: Int,
        reviewId: Long,
        installationId: Long,
    ) {
        val token = tokenPort.getInstallationToken(installationId)
        gitHubHttpClient.dismissPrReview(repositoryFullName, pullRequestNumber, reviewId, token)
    }
}
