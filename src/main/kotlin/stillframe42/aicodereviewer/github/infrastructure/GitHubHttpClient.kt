package stillframe42.aicodereviewer.github.infrastructure

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import stillframe42.aicodereviewer.github.adapter.out.github.dto.CreateIssueCommentRequest
import stillframe42.aicodereviewer.github.adapter.out.github.dto.InstallationTokenResponse

// GitHub REST API raw HTTP 호출을 캡슐화하는 클라이언트
// 모든 WebClient 호출은 이 클래스 한 곳에서 관리한다
@Component
class GitHubHttpClient(
    @param:Qualifier("gitHubWebClient") private val webClient: WebClient,
) {

    // POST /app/installations/{id}/access_tokens — JWT 인증으로 Installation Access Token 발급
    suspend fun fetchInstallationToken(installationId: Long, jwt: String): InstallationTokenResponse =
        webClient.post()
            .uri("/app/installations/{id}/access_tokens", installationId)
            .header("Authorization", "Bearer $jwt")
            .retrieve()
            .awaitBody()

    // GET /repos/{owner}/{repo}/pulls/{number} — unified diff 형식으로 PR diff 조회
    // Accept 헤더를 application/vnd.github.v3.diff로 오버라이드하여 diff 텍스트를 수신한다
    suspend fun fetchPrDiff(repositoryFullName: String, pullRequestNumber: Int, token: String): String {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return webClient.get()
            .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, pullRequestNumber)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3.diff")
            .retrieve()
            .awaitBody()
    }

    // POST /repos/{owner}/{repo}/issues/{number}/comments — PR에 이슈 코멘트 등록
    // GitHub PR 코멘트는 issues API를 통해 등록한다
    suspend fun postIssueComment(repositoryFullName: String, pullRequestNumber: Int, body: String, token: String) {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        webClient.post()
            .uri("/repos/{owner}/{repo}/issues/{number}/comments", owner, repo, pullRequestNumber)
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(CreateIssueCommentRequest(body = body))
            .retrieve()
            .awaitBodilessEntity()
    }
}

// "owner/repo" 형식의 문자열을 owner와 repo로 분리한다
private fun String.ownerAndRepo(): Pair<String, String> {
    val (owner, repo) = split("/", limit = 2)
    return owner to repo
}
