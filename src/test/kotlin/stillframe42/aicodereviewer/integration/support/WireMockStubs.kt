package stillframe42.aicodereviewer.integration.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo

// WireMock stub 등록 헬퍼 — 각 테스트의 @BeforeEach에서 필요한 stub을 조합해 사용한다.
// wireMock.resetAll()은 AbstractIntegrationTest.setUpBase()에서 처리하므로 여기서 호출하지 않는다.
object WireMockStubs {

    // GitHub Installation Access Token 발급
    // 토큰은 GitHubAppTokenProvider가 캐싱하므로 테스트 스위트 내 최초 1회만 실제 호출된다.
    fun stubInstallationToken(server: WireMockServer, installationId: Long) {
        server.stubFor(
            post(urlPathEqualTo("/app/installations/$installationId/access_tokens"))
                .willReturn(okJson("""{"token":"ghs_test_token","expires_at":"2099-12-31T23:59:59Z"}"""))
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
}
