package stillframe42.aicodereviewer.github.adapter.out.github.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import stillframe42.aicodereviewer.github.adapter.out.github.dto.CommitSummaryResponse
import stillframe42.aicodereviewer.github.adapter.out.github.dto.CreatePullRequestReviewRequest
import stillframe42.aicodereviewer.github.adapter.out.github.dto.DirectoryEntryResponse
import stillframe42.aicodereviewer.github.adapter.out.github.dto.FileContentResponse
import stillframe42.aicodereviewer.github.adapter.out.github.dto.InstallationTokenResponse
import stillframe42.aicodereviewer.github.adapter.out.github.dto.PrDescriptionResponse
import stillframe42.aicodereviewer.github.adapter.out.github.dto.PrFileResponse
import stillframe42.aicodereviewer.github.adapter.out.github.dto.PullRequestReviewResponse

// GitHub REST API raw HTTP 호출을 캡슐화하는 클라이언트
// 모든 RestClient 호출은 이 클래스 한 곳에서 관리한다
@Component
class GitHubHttpClient(
    @param:Qualifier("gitHubRestClient") private val restClient: RestClient,
) {

    // POST /app/installations/{id}/access_tokens — JWT 인증으로 Installation Access Token 발급
    fun fetchInstallationToken(installationId: Long, jwt: String): InstallationTokenResponse =
        restClient.post()
            .uri("/app/installations/{id}/access_tokens", installationId)
            .header("Authorization", "Bearer $jwt")
            .retrieve()
            .body(InstallationTokenResponse::class.java)!!

    // GET /repos/{owner}/{repo}/pulls/{number} — unified diff 형식으로 PR diff 조회
    // Accept 헤더를 application/vnd.github.v3.diff로 오버라이드하여 diff 텍스트를 수신한다
    fun fetchPrDiff(repositoryFullName: String, pullRequestNumber: Int, token: String): String {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return restClient.get()
            .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, pullRequestNumber)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3.diff")
            .retrieve()
            .body(String::class.java)!!
    }

    // GET /repos/{owner}/{repo}/pulls/{number}/files — PR 변경 파일 목록과 메타데이터 조회
    fun fetchPrFiles(
        repositoryFullName: String,
        pullRequestNumber: Int,
        token: String,
    ): List<PrFileResponse> {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return restClient.get()
            .uri("/repos/{owner}/{repo}/pulls/{number}/files", owner, repo, pullRequestNumber)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(object : ParameterizedTypeReference<List<PrFileResponse>>() {})!!
    }

    // GET /repos/{owner}/{repo}/contents/{path}?ref={ref} — 특정 ref의 파일 내용 조회
    // content는 MIME Base64 인코딩 + 개행 포함으로 반환된다
    // path에 슬래시가 포함되므로 템플릿 변수 대신 문자열 보간으로 URL을 구성한다 (슬래시 인코딩 방지)
    fun fetchFileContent(
        repositoryFullName: String,
        path: String,
        ref: String,
        token: String,
        installationId: Long,
    ): FileContentResponse {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return restClient.get()
            .uri { it.path("/repos/$owner/$repo/contents/$path").queryParam("ref", ref).build() }
            .header("Authorization", "Bearer $token")
            .attributes { it["installationId"] = installationId }
            .retrieve()
            .body(FileContentResponse::class.java)!!
    }

    // POST /repos/{owner}/{repo}/pulls/{number}/reviews — PR Reviews API로 코드 리뷰 등록, 생성된 review ID 반환
    fun postPrReview(
        repositoryFullName: String,
        pullRequestNumber: Int,
        request: CreatePullRequestReviewRequest,
        token: String,
    ): Long {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return restClient.post()
            .uri("/repos/{owner}/{repo}/pulls/{number}/reviews", owner, repo, pullRequestNumber)
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(PullRequestReviewResponse::class.java)!!
            .id
    }

    // PUT /repos/{owner}/{repo}/pulls/{number}/reviews/{reviewId}/dismissals — 기존 리뷰 dismiss
    // 새 커밋 push 시 이전 리뷰를 무효화하는 데 사용한다
    fun dismissPrReview(
        repositoryFullName: String,
        pullRequestNumber: Int,
        reviewId: Long,
        token: String,
    ) {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        restClient.put()
            .uri(
                "/repos/{owner}/{repo}/pulls/{number}/reviews/{reviewId}/dismissals",
                owner, repo, pullRequestNumber, reviewId,
            )
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("message" to "새 커밋이 push되어 이전 리뷰를 dismiss합니다."))
            .retrieve()
            .toBodilessEntity()
    }

    // GET /repos/{owner}/{repo}/contents/{path}?ref={ref} — 디렉토리 내 항목 목록 조회
    // path가 디렉토리를 가리킬 때 배열 응답을 반환한다
    // path가 빈 문자열이면 루트 디렉토리를 조회한다
    // 슬래시 인코딩 방지를 위해 템플릿 변수 대신 문자열 보간으로 URL을 구성한다
    fun fetchDirectoryContents(
        repositoryFullName: String,
        path: String,
        ref: String,
        token: String,
        installationId: Long,
    ): List<DirectoryEntryResponse> {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return restClient.get()
            .uri { it.path("/repos/$owner/$repo/contents/$path").queryParam("ref", ref).build() }
            .header("Authorization", "Bearer $token")
            .attributes { it["installationId"] = installationId }
            .retrieve()
            .body(object : ParameterizedTypeReference<List<DirectoryEntryResponse>>() {})!!
    }

    // GET /repos/{owner}/{repo}/pulls/{number} — PR 제목과 본문 조회
    fun fetchPrDescription(
        repositoryFullName: String,
        prNumber: Int,
        token: String,
        installationId: Long,
    ): PrDescriptionResponse {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return restClient.get()
            .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
            .header("Authorization", "Bearer $token")
            .attributes { it["installationId"] = installationId }
            .retrieve()
            .body(PrDescriptionResponse::class.java)!!
    }

    // GET /repos/{owner}/{repo}/commits?path={filePath}&per_page=5 — 파일 최근 커밋 이력 조회
    fun fetchFileCommitHistory(
        repositoryFullName: String,
        filePath: String,
        token: String,
        installationId: Long,
    ): List<CommitSummaryResponse> {
        val (owner, repo) = repositoryFullName.ownerAndRepo()
        return restClient.get()
            .uri { it.path("/repos/{owner}/{repo}/commits").queryParam("path", filePath).queryParam("per_page", 5).build(owner, repo) }
            .header("Authorization", "Bearer $token")
            .attributes { it["installationId"] = installationId }
            .retrieve()
            .body(object : ParameterizedTypeReference<List<CommitSummaryResponse>>() {})!!
    }
}

// "owner/repo" 형식의 문자열을 owner와 repo로 분리한다
private fun String.ownerAndRepo(): Pair<String, String> {
    val (owner, repo) = split("/", limit = 2)
    return owner to repo
}
