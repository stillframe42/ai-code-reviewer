package stillframe42.aicodereviewer.e2e.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario
import java.time.Duration

// 보안 PR (Remote 에이전트 경로) 의 모든 외부 호출 stub.
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

    // 일반 PR — installation token / GitHub PR-files / GitHub POST review / Anthropic /v1/messages / OpenAI embedding / langfuse stub.
    // OpenAI chat 시퀀스는 호출 안 함 — Spring AI 직접 경로는 Anthropic 1회로 끝난다.
    // 단, RAG 컨벤션 컨텍스트 빌딩(ConventionContextService)이 OpenAI 임베딩을 사용하므로 stubOpenAiEmbedding 은 필요하다.
    fun stubAllForGeneral(wm: WireMockServer, fixture: GeneralPrFixture) {
        stubGitHubInstallationToken(wm)
        stubGitHubGetPrGeneral(wm, fixture)
        stubGitHubGetPrFilesGeneral(wm, fixture)
        stubGitHubPostReview(wm, fixture.prNumber)
        stubOpenAiEmbedding(wm)
        stubAnthropicMessages(wm, fixture)
        stubLangfuse(wm)
    }

    // GET /repos/.../pulls/{n} — Accept 헤더 매칭 stub 우선 (priority 1), JSON fallback 후순위 (priority 10).
    // 운영 webClient 가 다중 Accept 헤더 ("application/vnd.github+json" default + ".v3.diff" override) 를 보낼 때
    // priority 명시 없이는 fallback 이 매칭되어 prDiff 가 빈 JSON 으로 흐를 수 있음 — 일반 PR 경로가 처음으로 노출시킨 케이스.
    private fun stubGitHubGetPrGeneral(wm: WireMockServer, fixture: GeneralPrFixture) {
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}"))
            .atPriority(1)
            .withHeader("Accept", containing("application/vnd.github.v3.diff"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/vnd.github.v3.diff")
                .withBody(fixture.prDiff)))
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}"))
            .atPriority(10)
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"number":${fixture.prNumber},"title":"test","body":""}""")))
    }

    // GET /repos/.../pulls/{n}/files
    private fun stubGitHubGetPrFilesGeneral(wm: WireMockServer, fixture: GeneralPrFixture) {
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}/files"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(fixture.prFilesJson)))
    }

    // POST /v1/messages — Spring AI Anthropic 어댑터의 호출 endpoint. 1회 응답으로 끝남.
    private fun stubAnthropicMessages(wm: WireMockServer, fixture: GeneralPrFixture) {
        wm.stubFor(post(urlMatching("/v1/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(fixture.anthropicReviewResponse)))
    }

    // 에이전트 다운 — installation token / GitHub PR-files / GitHub POST review /
    // Anthropic /v1/messages / OpenAI embedding / langfuse / agent ConnectionReset fault stub.
    // agent.remote.url 이 WireMock 으로 redirect 된 상태에서 /agent/analyze 호출이 connection reset 되어
    // RemoteAgentClient.mapHttpExceptions 가 AgentUnavailableException 으로 매핑 → fallbackToSpringAI("unavailable", ...).
    fun stubAllForAgentDown(wm: WireMockServer, fixture: AgentDownPrFixture) {
        stubGitHubInstallationToken(wm)
        stubGitHubGetPrAgentDown(wm, fixture)
        stubGitHubGetPrFilesAgentDown(wm, fixture)
        stubGitHubPostReview(wm, fixture.prNumber)
        stubOpenAiEmbedding(wm)
        stubAnthropicMessagesAgentDown(wm, fixture)
        stubLangfuse(wm)
        stubAgentConnectionReset(wm)
    }

    // POST /agent/analyze — connection reset fault 로 WebClientRequestException 유발.
    // AgentFallbackIntegrationTest 의 connection reset 시나리오와 동일 패턴.
    private fun stubAgentConnectionReset(wm: WireMockServer) {
        wm.stubFor(post(urlMatching("/agent/analyze"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))
    }

    // GET /repos/.../pulls/{n} — Accept 헤더 매칭 priority 1, JSON fallback 후순위 (stubGitHubGetPrGeneral 패턴 동일).
    private fun stubGitHubGetPrAgentDown(wm: WireMockServer, fixture: AgentDownPrFixture) {
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}"))
            .atPriority(1)
            .withHeader("Accept", containing("application/vnd.github.v3.diff"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/vnd.github.v3.diff")
                .withBody(fixture.prDiff)))
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}"))
            .atPriority(10)
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"number":${fixture.prNumber},"title":"test","body":""}""")))
    }

    private fun stubGitHubGetPrFilesAgentDown(wm: WireMockServer, fixture: AgentDownPrFixture) {
        wm.stubFor(get(urlMatching("/repos/stillframe42/ai-code-reviewer/pulls/${fixture.prNumber}/files"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(fixture.prFilesJson)))
    }

    // POST /v1/messages — Spring AI 폴백 경로의 Anthropic 호출. 1회 응답으로 끝남.
    private fun stubAnthropicMessagesAgentDown(wm: WireMockServer, fixture: AgentDownPrFixture) {
        wm.stubFor(post(urlMatching("/v1/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(fixture.anthropicReviewResponse)))
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
    // OpenAiEmbeddingBatchTransformer 가 input 배열 수에 맞게 동적 응답 생성.
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
