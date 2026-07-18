package stillframe42.aicodereviewer.e2e

import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import stillframe42.aicodereviewer.e2e.support.AgentDownPrFixture
import stillframe42.aicodereviewer.e2e.support.E2EAssertions
import stillframe42.aicodereviewer.e2e.support.WireMockScenarios

// Remote 에이전트 다운 → Spring AI 폴백 → PR 코멘트 등록.
// 핵심 박제:
//   (1) /agent/analyze 호출이 connection reset → AgentUnavailableException → 폴백 분기
//   (2) agent.fallback.count{reason=unavailable} +1
//   (3) /actuator/health 의 components.remoteAgent.status = DOWN
//
// 다운 시뮬레이션 메커니즘:
//   - companion 의 static init 에서 System property `e2e.agent.remote.url.override` 를 WireMock URL 로 설정 →
//     AbstractE2ETest.overrideProperties 의 supplier 가 그 값을 우선 사용 (companion init 은 Spring 컨텍스트 부팅 전에 실행).
//   - WireMock 에 POST /agent/analyze 의 ConnectionReset fault stub → 호출 시 ResourceAccessException → AgentUnavailableException.
//   - @TestPropertySource 의 marker 가 컨텍스트 cache key 를 분리 → 다른 e2e 테스트 컨텍스트와 독립 (HikariPool +1).
//   - @AfterAll 에서 System property 클리어 → 다른 e2eTest 영향 0.
@TestPropertySource(properties = ["e2e.scenario=agent-down"])
class AgentDownE2ETest : AbstractE2ETest() {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    companion object {
        init {
            // companion init 은 Spring 컨텍스트 부팅 전에 실행 — supplier 평가 시 System property 가 이미 set 상태.
            System.setProperty("e2e.agent.remote.url.override", "http://localhost:${wireMock.port()}")
        }

        @JvmStatic
        @AfterAll
        fun clearAgentUrlOverride() {
            System.clearProperty("e2e.agent.remote.url.override")
        }
    }

    @Test
    fun `Remote 에이전트 다운 → Spring AI 폴백 → 메트릭과 헬스 모두 다운 상태 노출`() {
        val fixture = AgentDownPrFixture.load()
        WireMockScenarios.stubAllForAgentDown(wireMock, fixture)
        val baseline = E2EAssertions.fallbackMetricBaseline(meterRegistry, reason = "unavailable")

        val response = client.post()
            .uri("/api/github/webhook")
            .header("X-Hub-Signature-256", fixture.webhookSignature)
            .header("X-GitHub-Event", "pull_request")
            .header("Content-Type", "application/json")
            .body(fixture.webhookPayload)
            .exchange()

        E2EAssertions.assertWebhookAccepted(response)
        E2EAssertions.assertPrReviewSubmitted(wireMock, fixture.prNumber)
        E2EAssertions.assertFallbackMetricIncremented(meterRegistry, "unavailable", baseline)
        E2EAssertions.assertRemoteAgentHealthDown(client)
        E2EAssertions.assertFallbackMetricExposedAsPrometheus(client, "unavailable")
    }
}
