package stillframe42.aicodereviewer.e2e.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.jayway.jsonpath.JsonPath
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Duration

// 9단계 박제 함수 집합. 단계 (2)~(9) 는 fire-and-forget 백그라운드 처리라 Awaitility 폴링 필수.
// 함수 시그니처는 단계와 1:1 대응 — 호출 순서 자체가 시나리오 흐름을 가시화.
object E2EAssertions {

    private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
    private val FAST_TIMEOUT: Duration = Duration.ofSeconds(10)

    // 단계 (1): Webhook 수신 — 202 ACCEPTED
    fun assertWebhookAccepted(response: RestTestClient.ResponseSpec) {
        response.expectStatus().isAccepted
    }

    // 단계 (2)+(3): SECURITY 분기 + RAG 컨텍스트 조회
    // POST /agent/analyze 는 Remote 에이전트 endpoint 이므로 WireMock 에서 캡처 불가 — 대안:
    //   (a) Spring Boot 의 OpenAI 임베딩 호출 (/v1/embeddings) 1회 이상 = ConventionContextService 가 RAG 검색 수행
    //   (b) Remote 에이전트 stdout 에 "SECURITY" 마커 = SECURITY 분기로 Remote 에 도달
    fun assertRagContextIdsPassed(wm: WireMockServer, logs: ContainerLogTail) {
        await atMost DEFAULT_TIMEOUT untilAsserted {
            val embeddingCount = wm.allServeEvents.count {
                it.request.url.startsWith("/v1/embeddings") && it.request.method.value() == "POST"
            }
            assertThat(embeddingCount)
                .withFailMessage("/v1/embeddings 호출 0건 — RAG buildContextIds 미수행 (단계 3)")
                .isGreaterThanOrEqualTo(1)
        }
        val securityMarker = logs.await(FAST_TIMEOUT) {
            it.contains("SECURITY") || it.contains("security_agent")
        }
        assertThat(securityMarker)
            .withFailMessage("Remote 에이전트 stdout 에서 SECURITY 분기 마커 미발견 (단계 2)")
            .isTrue
    }

    // 단계 (4): Remote 에이전트 컨테이너 stdout 에 분석 시작 흔적
    fun assertRemoteAgentReached(logs: ContainerLogTail) {
        val hit = logs.await(FAST_TIMEOUT) {
            it.contains("agent_node") || it.contains("run_security_agent") || it.contains("/agent/analyze")
        }
        assertThat(hit).withFailMessage("Remote 에이전트 stdout 에서 분석 시작 흔적 미발견 (단계 4)")
            .isTrue
    }

    // 단계 (5): LangGraph 실행 — agent_node / extract_issues / run_security_agent 등의 node 마커
    fun assertLangGraphTraversed(logs: ContainerLogTail) {
        val hit = logs.await(FAST_TIMEOUT) {
            it.contains("agent_node") || it.contains("extract_issues") ||
                it.contains("run_security_agent") || it.contains("security_agent")
        }
        assertThat(hit).withFailMessage("LangGraph node traversal 마커 미발견 (단계 5)")
            .isTrue
    }

    // 단계 (6): OWASP Tool 호출 — scenario state 가 ToolCalled/Done 으로 전이
    fun assertOwaspToolCalled(wm: WireMockServer) {
        await atMost FAST_TIMEOUT untilAsserted {
            // Awaitility 의 untilAsserted 는 AssertionError 만 retry — error() (IllegalStateException) 사용 금지.
            val scenario = wm.allScenarios.scenarios.firstOrNull { it.name == "openai-chat" }
            assertThat(scenario).withFailMessage("openai-chat scenario 미설정").isNotNull
            assertThat(scenario!!.state)
                .withFailMessage("LangGraph 가 Tool 호출 결정을 내리지 않음 — state=%s", scenario.state)
                .isIn("ToolCalled", "Done")
        }
    }

    // 단계 (7): 구조화된 이슈 반환 — Spring Boot 가 받은 응답을 GitHub PR review 로 흘려보낸 흔적
    // PrReview body 안에 보안 키워드 (description 의 일부) 가 포함되어야 함
    fun assertSecurityIssuesPresent(wm: WireMockServer, prNumber: Int) {
        await atMost DEFAULT_TIMEOUT untilAsserted {
            val reviewEvent = wm.allServeEvents.firstOrNull {
                it.request.method.value() == "POST" &&
                    it.request.url.matches(Regex(".*/pulls/$prNumber/reviews"))
            }
            assertThat(reviewEvent)
                .withFailMessage("POST /pulls/$prNumber/reviews 요청 미도달 (단계 7)")
                .isNotNull

            val body = reviewEvent!!.request.bodyAsString
            assertThat(body)
                .withFailMessage("PR review body 에 보안 이슈 흔적 없음:\n%s", body)
                .containsAnyOf("CWE-256", "평문 패스워드", "plaintext", "HIGH")
        }
    }

    // 단계 (8): Spring Boot 매핑 — AgentIssue → CodeIssue → PrReview 매핑 정합성
    // event=REQUEST_CHANGES (이슈가 있을 때 흐름) 가 핵심 박제
    fun assertCodeReviewMappedCorrectly(wm: WireMockServer, prNumber: Int) {
        val reviewEvent = wm.allServeEvents.first {
            it.request.method.value() == "POST" &&
                it.request.url.matches(Regex(".*/pulls/$prNumber/reviews"))
        }
        val body = JsonPath.parse(reviewEvent.request.bodyAsString)
        val event = body.read<String>("$.event")
        assertThat(event)
            .withFailMessage("event 가 REQUEST_CHANGES 아님 (issues 가 매핑 안 됨): %s", event)
            .isEqualTo("REQUEST_CHANGES")
    }

    // 단계 (9): PR 리뷰 등록 호출 자체 — WireMock verify
    fun assertPrReviewSubmitted(wm: WireMockServer, prNumber: Int) {
        await atMost DEFAULT_TIMEOUT untilAsserted {
            wm.verify(postRequestedFor(urlMatching(".*/pulls/$prNumber/reviews")))
        }
    }

    // 시나리오 2 핵심 박제: Remote 에이전트의 POST /agent/analyze 가 0회 호출됨.
    // 양성 결과(`assertPrReviewSubmitted`) 가 도착한 후 호출하는 것이 안전하다 —
    // fire-and-forget 처리가 진행 중일 때 verify(0) 가 거짓 통과할 위험 회피.
    fun assertRemoteAgentNotCalled(wm: WireMockServer) {
        wm.verify(0, postRequestedFor(urlMatching("/agent/analyze")))
    }

    // 시나리오 3 박제: agent.fallback.count{reason} 카운터가 baseline + 1 이상으로 증가했음을 await.
    // baseline 은 테스트 진입 직전 fallbackMetricBaseline() 로 측정. 다른 테스트 누적분 격리 위함.
    // fire-and-forget 백그라운드 처리라 PR 코멘트 등록(`assertPrReviewSubmitted`) 이후 호출하는 것이 안전.
    fun assertFallbackMetricIncremented(meterRegistry: MeterRegistry, reason: String, baseline: Double) {
        await atMost DEFAULT_TIMEOUT untilAsserted {
            val current = meterRegistry.find("agent.fallback.count")
                .tag("reason", reason)
                .counter()
                ?.count() ?: 0.0
            assertThat(current)
                .withFailMessage(
                    "agent.fallback.count{reason=%s} 증가 안 됨 — baseline=%.1f, current=%.1f",
                    reason, baseline, current,
                )
                .isGreaterThanOrEqualTo(baseline + 1.0)
        }
    }

    // 진입 시점 baseline 측정 헬퍼 — 카운터가 아직 등록 안 됐을 수 있으므로 ?: 0.0
    fun fallbackMetricBaseline(meterRegistry: MeterRegistry, reason: String): Double =
        meterRegistry.find("agent.fallback.count")
            .tag("reason", reason)
            .counter()
            ?.count() ?: 0.0

    // 시나리오 3 박제: /actuator/health 의 components.remoteAgent.status 가 DOWN 으로 노출됨을 await.
    // ReactiveHealthIndicator 가 매 호출 port.checkHealth() 직접 호출 (캐시 없음) — 다운 상태가 즉시 반영됨.
    // application-e2e-test.yml 의 management.endpoint.health.show-details=always 설정으로 components 노출됨.
    fun assertRemoteAgentHealthDown(client: RestTestClient) {
        await atMost DEFAULT_TIMEOUT untilAsserted {
            // status code 는 의도적으로 검증하지 않는다 — components.remoteAgent.status 가 DOWN 이라도
            // Spring Boot 의 HttpCodeStatusMapper 설정에 따라 전체 status 가 200 또는 503 어느 쪽이든 가능.
            val body = client.get()
                .uri("/actuator/health")
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody
                ?: error("/actuator/health 응답 본문 없음")
            val status = JsonPath.parse(body).read<String>("$.components.remoteAgent.status")
            assertThat(status)
                .withFailMessage("components.remoteAgent.status 가 DOWN 아님: %s\nbody=%s", status, body)
                .isEqualTo("DOWN")
        }
    }
}
