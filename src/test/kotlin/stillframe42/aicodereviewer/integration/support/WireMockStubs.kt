package stillframe42.aicodereviewer.integration.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.github.tomakehurst.wiremock.verification.LoggedRequest

// WireMock stub 등록 헬퍼 — 각 테스트의 @BeforeEach에서 필요한 stub을 조합해 사용한다.
// wireMock.resetAll()은 AbstractIntegrationTest.setUpBase()에서 처리하므로 여기서 호출하지 않는다.
object WireMockStubs {

    // 플로우 검증 테스트에서 공통으로 사용하는 고정 상수
    const val TEST_INSTALLATION_ID = 12345678L
    const val TEST_REPO = "owner/repo"
    const val TEST_PR_NUMBER = 42

    // GitHub Installation Access Token 발급
    // 토큰은 GitHubAppTokenProvider가 캐싱하므로 테스트 스위트 내 최초 1회만 실제 호출된다.
    fun stubInstallationToken(server: WireMockServer, installationId: Long) {
        server.stubFor(
            post(urlPathEqualTo("/app/installations/$installationId/access_tokens"))
                .willReturn(okJson("""{"token":"ghs_test_token","expires_at":"2099-12-31T23:59:59Z"}"""))
        )
    }

    // 모든 Installation ID에 대한 토큰 발급 — ID별 구분이 불필요한 테스트에서 사용
    fun stubAnyInstallationToken(server: WireMockServer) {
        server.stubFor(
            post(urlPathMatching("/app/installations/\\d+/access_tokens"))
                .willReturn(okJson("""{"token":"ghs_test_token","expires_at":"2099-12-31T23:59:59Z"}"""))
        )
    }

    // Anthropic 채팅 응답 (비스트리밍) — DefaultChatService / ChatController 테스트용
    fun stubAnthropicChat(server: WireMockServer) {
        server.stubFor(
            post(urlPathEqualTo("/v1/messages"))
                .willReturn(okJson(AnthropicResponseFixtures.CHAT_SUCCESS))
        )
    }

    // Anthropic 채팅 스트리밍 응답 — /api/chat/stream 테스트용
    // "stream":true 요청 본문 매처로 비스트리밍 스텁보다 우선 매칭된다
    fun stubAnthropicChatStream(server: WireMockServer) {
        server.stubFor(
            post(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("\"stream\":true"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(AnthropicResponseFixtures.CHAT_STREAM_SUCCESS)
                )
        )
    }

    // Anthropic 이슈 포함 리뷰 응답 — DefaultReviewServiceTest 이슈 감지 테스트용
    fun stubAnthropicReviewWithIssues(server: WireMockServer) {
        server.stubFor(
            post(urlPathEqualTo("/v1/messages"))
                .willReturn(okJson(AnthropicResponseFixtures.REVIEW_WITH_ISSUES))
        )
    }

    // GitHub 파일 내용 조회 — GET /repos/{owner}/{repo}/contents/{path}?ref={ref}
    // content는 "file content"의 Base64 인코딩 값
    fun stubGitHubFileContent(
        server: WireMockServer,
        repo: String,
        path: String,
        ref: String,
        encodedContent: String = "ZmlsZSBjb250ZW50",
    ) {
        val (owner, repoName) = repo.split("/", limit = 2)
        val fileName = path.substringAfterLast("/")
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/contents/$path"))
                .withQueryParam("ref", equalTo(ref))
                .willReturn(
                    okJson("""{"name":"$fileName","path":"$path","size":12,"content":"$encodedContent","encoding":"base64"}""")
                )
        )
    }

    // GitHub 파일/디렉토리 404 — 존재하지 않는 경로에 대한 Not Found 응답
    fun stubGitHubNotFound(server: WireMockServer, repo: String, path: String, ref: String) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/contents/$path"))
                .withQueryParam("ref", equalTo(ref))
                .willReturn(aResponse().withStatus(404).withBody("""{"message":"Not Found"}"""))
        )
    }

    // GitHub 디렉토리 목록 조회 — path="" 이면 루트 디렉토리, 아니면 해당 경로의 목록 반환
    fun stubGitHubDirectoryContents(
        server: WireMockServer,
        repo: String,
        path: String,
        ref: String,
        entries: String = "[]",
    ) {
        val (owner, repoName) = repo.split("/", limit = 2)
        // 루트 디렉토리는 trailing slash 유무가 불확실하므로 regex 매처 사용
        val matcher = if (path.isEmpty()) {
            urlPathMatching("/repos/$owner/$repoName/contents/?")
        } else {
            urlPathEqualTo("/repos/$owner/$repoName/contents/$path")
        }
        server.stubFor(
            get(matcher)
                .withQueryParam("ref", equalTo(ref))
                .willReturn(okJson(entries))
        )
    }

    // GitHub PR 제목/설명 조회 — diff Accept 헤더 없는 GET /repos/{owner}/{repo}/pulls/{number}
    fun stubGitHubPrDescription(server: WireMockServer, repo: String, prNumber: Int) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber"))
                .willReturn(okJson("""{"title":"테스트 PR","body":"테스트 설명입니다."}"""))
        )
    }

    // GitHub PR Not Found — 존재하지 않는 PR 번호에 대한 404 응답
    fun stubGitHubPrNotFound(server: WireMockServer, repo: String, prNumber: Int) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber"))
                .willReturn(aResponse().withStatus(404).withBody("""{"message":"Not Found"}"""))
        )
    }

    // GitHub 파일 커밋 이력 조회 — GET /repos/{owner}/{repo}/commits?path={filePath}&per_page=5
    fun stubGitHubCommitHistory(server: WireMockServer, repo: String, filePath: String) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/commits"))
                .withQueryParam("path", equalTo(filePath))
                .willReturn(
                    okJson(
                        """[{"sha":"abc1234def5678","commit":{"message":"feat: 초기 커밋","author":{"name":"Test User","date":"2024-01-01T00:00:00Z"}}}]"""
                    )
                )
        )
    }

    // GitHub 커밋 이력 없음 — GitHub는 존재하지 않는 파일 경로에도 200 + 빈 배열 반환
    fun stubGitHubCommitHistoryEmpty(server: WireMockServer, repo: String, filePath: String) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/commits"))
                .withQueryParam("path", equalTo(filePath))
                .willReturn(okJson("[]"))
        )
    }

    // PR unified diff 조회 — Accept: application/vnd.github.v3.diff 헤더로 구분
    fun stubPrDiff(server: WireMockServer, repo: String, prNumber: Int, diff: String) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber"))
                .withHeader("Accept", containing("diff"))
                .willReturn(aResponse().withStatus(200).withBody(diff))
        )
    }

    // PR 변경 파일 목록 조회
    fun stubPrFiles(server: WireMockServer, repo: String, prNumber: Int) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber/files"))
                .willReturn(okJson("""[{"filename":"src/Foo.kt","changes":3,"status":"modified","additions":3,"deletions":0}]"""))
        )
    }

    // Anthropic AI 리뷰 응답 — Spring AI가 파싱할 수 있는 형식 반환
    fun stubAnthropicReview(server: WireMockServer) {
        server.stubFor(
            post(urlPathEqualTo("/v1/messages"))
                .willReturn(okJson(AnthropicResponseFixtures.REVIEW_SUCCESS))
        )
    }

    // PR Reviews 등록 — 검증의 핵심 대상
    fun stubPostPrReview(server: WireMockServer, repo: String, prNumber: Int, reviewId: Long = 9001L) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            post(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber/reviews"))
                .willReturn(okJson("""{"id":$reviewId}"""))
        )
    }

    // PR dismiss 리뷰 — 이전 리뷰가 있을 때 dismiss 시도
    fun stubDismissPrReview(server: WireMockServer, repo: String, prNumber: Int, reviewId: Long) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            put(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber/reviews/$reviewId/dismissals"))
                .willReturn(aResponse().withStatus(200))
        )
    }

    // Anthropic AI 오류 — 500 반환으로 SpringAiReviewAdapter 재시도 후 실패 유도
    fun stubAnthropicError(server: WireMockServer) {
        server.stubFor(
            post(urlPathEqualTo("/v1/messages"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
        )
    }

    // PR diff 오류 — 500 반환으로 getPrDiff 실패 유도 (handlePullRequestEvent 전체 실패)
    fun stubPrDiffError(server: WireMockServer, repo: String, prNumber: Int) {
        val (owner, repoName) = repo.split("/", limit = 2)
        server.stubFor(
            get(urlPathEqualTo("/repos/$owner/$repoName/pulls/$prNumber"))
                .withHeader("Accept", containing("diff"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
        )
    }

    // Langfuse ingestion API stub — POST /api/public/ingestion에 200 응답
    fun stubLangfuseIngestion(server: WireMockServer) {
        server.stubFor(
            post(urlPathEqualTo("/api/public/ingestion"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"successes":[],"errors":[]}""")
                )
        )
    }

    // Langfuse로 전송된 모든 ingestion 요청 반환
    fun findLangfuseIngestionRequests(server: WireMockServer): List<LoggedRequest> =
        server.findAll(postRequestedFor(urlPathEqualTo("/api/public/ingestion")))

    // Anthropic Tool Calling 시나리오 stub — 1차: tool_use 응답, 2차: 최종 리뷰 응답
    fun stubAnthropicWithToolCall(server: WireMockServer) {
        server.stubFor(
            post(urlPathEqualTo("/v1/messages"))
                .inScenario("tool-calling")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("after-tool")
                .willReturn(okJson(AnthropicResponseFixtures.REVIEW_WITH_TOOL_CALL))
        )
        server.stubFor(
            post(urlPathEqualTo("/v1/messages"))
                .inScenario("tool-calling")
                .whenScenarioStateIs("after-tool")
                .willReturn(okJson(AnthropicResponseFixtures.REVIEW_SUCCESS))
        )
    }
}
