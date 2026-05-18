package stillframe42.aicodereviewer.e2e

import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.e2e.support.E2EAssertions
import stillframe42.aicodereviewer.e2e.support.GeneralPrFixture
import stillframe42.aicodereviewer.e2e.support.WireMockScenarios

// 시나리오 2 — 일반 PR webhook → Spring AI 직접 경로 → PR 코멘트 등록.
// 핵심 박제: Remote 에이전트 컨테이너의 POST /agent/analyze 가 0회 호출됨.
// assertion 호출 순서: PR 코멘트 등록 완료 후 verify(0) — fire-and-forget 처리 완료를 보장.
class GeneralPrE2ETest : AbstractE2ETest() {

    @Test
    fun `일반 PR webhook → Spring AI 직접 경로 → PR 코멘트 등록 (Remote 에이전트 호출 0회)`() {
        val fixture = GeneralPrFixture.load()
        WireMockScenarios.stubAllForGeneral(wireMock, fixture)

        val response = client.post()
            .uri("/api/github/webhook")
            .header("X-Hub-Signature-256", fixture.webhookSignature)
            .header("X-GitHub-Event", "pull_request")
            .header("Content-Type", "application/json")
            .body(fixture.webhookPayload)
            .exchange()

        E2EAssertions.assertWebhookAccepted(response)
        E2EAssertions.assertPrReviewSubmitted(wireMock, fixture.prNumber)
        E2EAssertions.assertRemoteAgentNotCalled(wireMock)
    }
}
