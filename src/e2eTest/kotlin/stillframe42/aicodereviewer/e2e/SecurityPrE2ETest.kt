package stillframe42.aicodereviewer.e2e

import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.e2e.support.E2EAssertions
import stillframe42.aicodereviewer.e2e.support.SecurityPrFixture
import stillframe42.aicodereviewer.e2e.support.WireMockScenarios

// 시나리오 1 — 보안 PR webhook → Remote 에이전트 → PR 리뷰 등록 happy path.
// 9단계 assertion 의 호출 순서 자체가 흐름을 가시화한다.
class SecurityPrE2ETest : AbstractE2ETest() {

    @Test
    fun `보안 PR webhook → Remote 에이전트 → PR 리뷰 등록 9단계 happy path`() {
        // given
        val fixture = SecurityPrFixture.load()
        WireMockScenarios.stubAll(wireMock, fixture)

        // when
        val response = client.post()
            .uri("/api/github/webhook")
            .header("X-Hub-Signature-256", fixture.webhookSignature)
            .header("X-GitHub-Event", "pull_request")
            .header("Content-Type", "application/json")
            .body(fixture.webhookPayload)
            .exchange()

        // then — 9단계 박제
        E2EAssertions.assertWebhookAccepted(response)                                    // 1
        E2EAssertions.assertRagContextIdsPassed(wireMock, fixture.prNumber)             // 2+3
        E2EAssertions.assertRemoteAgentReached(remoteAgentLogs)                          // 4
        E2EAssertions.assertLangGraphTraversed(remoteAgentLogs)                          // 5
        E2EAssertions.assertOwaspToolCalled(wireMock)                                    // 6
        E2EAssertions.assertSecurityIssuesPresent(wireMock, fixture.prNumber)            // 7
        E2EAssertions.assertCodeReviewMappedCorrectly(wireMock, fixture.prNumber)        // 8
        E2EAssertions.assertPrReviewSubmitted(wireMock, fixture.prNumber)                // 9
    }
}
