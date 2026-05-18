package stillframe42.aicodereviewer.e2e.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario
import java.time.Duration

// 시나리오 1 의 모든 외부 호출 stub.
// 가장 까다로운 부분은 stubOpenAiChatSequence — LangGraph 의 Tool 사용 결정 → 실행 → 최종 답 흐름을 scenario state 로 강제.
object WireMockScenarios {

    fun stubAll(wm: WireMockServer, fixture: SecurityPrFixture, chatDelay: Duration = Duration.ZERO) {
        stubGitHubInstallationToken(wm)
        stubGitHubGetPr(wm, fixture)
        stubGitHubGetPrFiles(wm, fixture)
        stubGitHubPostReview(wm, fixture.prNumber)
        stubOpenAiEmbedding(wm)
        stubOpenAiChatSequence(wm, fixture, chatDelay)
        stubLangfuse(wm)
    }

    // GitHub App installation access token 발급 stub
    private fun stubGitHubInstallationToken(wm: WireMockServer) {
        wm.stubFor(post(urlMatching("/app/installations/\\d+/access_tokens"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"token":"ghs_test_token","expires_at":"2099-01-01T00:00:00Z"}""")))
    }

    // GET /repos/{owner}/{repo}/pulls/{n} — Accept: application/vnd.github.v3.diff (v3 포함, GitHubHttpClient.fetchPrDiff 와 정합)
    private fun stubGitHubGetPr(wm: WireMockServer, fixture: SecurityPrFixture) {
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}"))
            .withHeader("Accept", equalTo("application/vnd.github.v3.diff"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/vnd.github.v3.diff")
                .withBody(fixture.prDiff)))
        // 일반 JSON 요청도 받을 수 있도록 fallback (다른 곳에서 PR metadata 조회 가능)
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"number":${fixture.prNumber},"title":"test","body":""}""")))
    }

    // GET /repos/.../pulls/{n}/files
    private fun stubGitHubGetPrFiles(wm: WireMockServer, fixture: SecurityPrFixture) {
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}/files"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(fixture.prFilesJson)))
    }

    // POST /repos/.../pulls/{n}/reviews — 단계 (9) 검증 대상
    // PUT .../reviews/{id}/dismissals 도 함께 stub (dismissPreviousReview 가 호출할 수 있음 — 처음 처리라 호출 안 되지만 안전망)
    private fun stubGitHubPostReview(wm: WireMockServer, prNumber: Int) {
        wm.stubFor(post(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/$prNumber/reviews"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":12345,"state":"COMMENTED"}""")))
        wm.stubFor(put(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/$prNumber/reviews/.+/dismissals"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{}")))
    }

    // POST /v1/embeddings — Spring Boot 의 RAG 임베딩.
    // OpenAiEmbeddingBatchTransformer (Phase 1) 가 input 배열 수에 맞게 동적 응답 생성.
    private fun stubOpenAiEmbedding(wm: WireMockServer) {
        wm.stubFor(post(urlMatching("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withTransformers("openai-embedding-batch")))
    }

    // POST /v1/chat/completions — Remote 에이전트의 LangGraph 호출. Scenario state 로 시퀀스 강제.
    // Started → ToolCalled → Done. chatDelay 가 ZERO 면 즉시 응답, > 0 이면 .withFixedDelay 로 LLM 지연 시뮬레이션.
    private fun stubOpenAiChatSequence(
        wm: WireMockServer,
        fixture: SecurityPrFixture,
        chatDelay: Duration = Duration.ZERO,
    ) {
        val delayMs = chatDelay.toMillis().toInt()

        wm.stubFor(post(urlMatching("/v1/chat/completions"))
            .inScenario("openai-chat")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(jsonBody(fixture.openAiToolCallResponse, delayMs))
            .willSetStateTo("ToolCalled"))

        wm.stubFor(post(urlMatching("/v1/chat/completions"))
            .inScenario("openai-chat")
            .whenScenarioStateIs("ToolCalled")
            .willReturn(jsonBody(fixture.openAiFinalIssuesResponse, delayMs))
            .willSetStateTo("Done"))

        // LangGraph 가 Done 상태 후에도 추가 호출 (extract_issues, agent_node retry 등) 을 할 수 있으므로
        // self-loop 로 final issues 응답을 계속 반환.
        wm.stubFor(post(urlMatching("/v1/chat/completions"))
            .inScenario("openai-chat")
            .whenScenarioStateIs("Done")
            .willReturn(jsonBody(fixture.openAiFinalIssuesResponse, delayMs)))
    }

    private fun jsonBody(body: String, delayMs: Int) =
        aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
            .let { if (delayMs > 0) it.withFixedDelay(delayMs) else it }

    // Langfuse ingestion — 안 깨지게만. 응답 형식은 정확하지 않아도 됨.
    private fun stubLangfuse(wm: WireMockServer) {
        wm.stubFor(post(urlMatching("/api/public/ingestion"))
            .willReturn(aResponse().withStatus(207)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"successes":[],"errors":[]}""")))
    }
}
