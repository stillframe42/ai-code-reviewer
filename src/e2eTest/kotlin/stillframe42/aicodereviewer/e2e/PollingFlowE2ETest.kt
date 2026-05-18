package stillframe42.aicodereviewer.e2e

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.e2e.support.E2EAssertions
import stillframe42.aicodereviewer.e2e.support.PollingAssertions
import stillframe42.aicodereviewer.e2e.support.SecurityPrFixture
import stillframe42.aicodereviewer.e2e.support.WireMockScenarios
import java.time.Duration

// Remote 에이전트의 LLM 호출에 의도적 지연 주입 → AgentPoller 가 IN_PROGRESS 여러 회 폴링 후 DONE.
// 9단계 happy path 와 별개 — 폴링 동작 자체를 박제.
class PollingFlowE2ETest : AbstractE2ETest() {

    @BeforeEach
    fun attachAgentPollerLogs() {
        springBootLogs.reset()
        springBootLogs.attachTo("stillframe42.aicodereviewer.agent.application.AgentPoller")
    }

    @Test
    fun `LLM 지연 주입 시 polling 이 여러 회 IN_PROGRESS 후 DONE 으로 종료`() {
        // given
        val fixture = SecurityPrFixture.load()
        // chat 호출 1건당 1초 지연 → LangGraph 3+ 호출 시 누적 ~3초 → polling ~6 attempts (500ms interval)
        WireMockScenarios.stubAll(wireMock, fixture, chatDelay = Duration.ofSeconds(1))

        // when
        val response = client.post()
            .uri("/api/github/webhook")
            .header("X-Hub-Signature-256", fixture.webhookSignature)
            .header("X-GitHub-Event", "pull_request")
            .header("Content-Type", "application/json")
            .body(fixture.webhookPayload)
            .exchange()

        // then
        E2EAssertions.assertWebhookAccepted(response)
        PollingAssertions.assertPollingHappened(springBootLogs, minAttempts = 3, maxAttempts = 15)
        PollingAssertions.assertPollingIntervalRoughlyMatches(
            logs = springBootLogs,
            expectedIntervalMs = 500,
            toleranceFactor = 0.5,
        )
    }
}
