package stillframe42.aicodereviewer.github.adapter.out.github

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.domain.model.PrFile
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

    // PR에 이슈 코멘트를 등록한다
    override suspend fun postReviewComment(
        repositoryFullName: String,
        pullRequestNumber: Int,
        comment: String,
        installationId: Long,
    ) {
        val token = tokenPort.getInstallationToken(installationId)
        gitHubHttpClient.postIssueComment(repositoryFullName, pullRequestNumber, comment, token)
    }
}
