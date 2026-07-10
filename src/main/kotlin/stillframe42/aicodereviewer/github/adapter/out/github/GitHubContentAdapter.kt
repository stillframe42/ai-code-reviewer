package stillframe42.aicodereviewer.github.adapter.out.github

import java.util.Base64
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.github.adapter.out.github.client.GitHubHttpClient
import stillframe42.aicodereviewer.github.adapter.out.github.dto.CommitSummaryResponse
import stillframe42.aicodereviewer.github.adapter.out.github.dto.DirectoryEntryResponse
import stillframe42.aicodereviewer.github.domain.model.FileCommitSummary
import stillframe42.aicodereviewer.github.domain.model.PrDescription
import stillframe42.aicodereviewer.github.domain.model.RepositoryEntry
import stillframe42.aicodereviewer.github.domain.model.RepositoryEntryType
import stillframe42.aicodereviewer.github.domain.port.out.GitHubContentPort
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort

// GitHubContentPort 구현 — 토큰 해석과 DTO→도메인 매핑을 담당한다
// HTTP 에러(WebClientResponseException)는 그대로 전파한다 — 소비자가 안내 메시지로 변환
@Component
class GitHubContentAdapter(
    private val gitHubHttpClient: GitHubHttpClient,
    private val tokenPort: GitHubTokenPort,
) : GitHubContentPort {

    override suspend fun getFileContent(repositoryFullName: String, path: String, ref: String, installationId: Long): String {
        val token = tokenPort.getInstallationToken(installationId)
        val response = gitHubHttpClient.fetchFileContent(repositoryFullName, path, ref, token, installationId)
        return Base64.getMimeDecoder().decode(response.content).toString(Charsets.UTF_8)
    }

    override suspend fun getDirectoryEntries(repositoryFullName: String, path: String, ref: String, installationId: Long): List<RepositoryEntry> {
        val token = tokenPort.getInstallationToken(installationId)
        return gitHubHttpClient.fetchDirectoryContents(repositoryFullName, path, ref, token, installationId)
            .map { it.toDomain() }
    }

    override suspend fun getPrDescription(repositoryFullName: String, prNumber: Int, installationId: Long): PrDescription {
        val token = tokenPort.getInstallationToken(installationId)
        val response = gitHubHttpClient.fetchPrDescription(repositoryFullName, prNumber, token, installationId)
        return PrDescription(title = response.title, body = response.body)
    }

    override suspend fun getFileCommitHistory(repositoryFullName: String, filePath: String, installationId: Long): List<FileCommitSummary> {
        val token = tokenPort.getInstallationToken(installationId)
        return gitHubHttpClient.fetchFileCommitHistory(repositoryFullName, filePath, token, installationId)
            .map { it.toDomain() }
    }
}

private fun DirectoryEntryResponse.toDomain() = RepositoryEntry(
    path = path,
    type = when (type) {
        "file" -> RepositoryEntryType.FILE
        "dir" -> RepositoryEntryType.DIR
        else -> RepositoryEntryType.OTHER
    },
)

private fun CommitSummaryResponse.toDomain() = FileCommitSummary(
    sha = sha,
    message = commit.message,
    authorName = commit.author.name,
    date = commit.author.date,
)
