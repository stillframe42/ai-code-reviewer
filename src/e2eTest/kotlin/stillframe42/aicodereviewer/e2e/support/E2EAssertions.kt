package stillframe42.aicodereviewer.e2e.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.jayway.jsonpath.JsonPath
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
}
