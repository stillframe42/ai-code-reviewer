package stillframe42.aicodereviewer.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.e2e.support.E2EAssertions
import stillframe42.aicodereviewer.e2e.support.SecurityPrFixture
import stillframe42.aicodereviewer.e2e.support.WireMockScenarios

// 보안 PR 흐름의 외부 관찰자 시점 elapsed (webhook POST → PR review verify 통과) 를 1회 측정·박제.
// 10회 반복 p50/p95/max 정량 측정은 docs/operations/e2e-elapsed-baseline.md 의 향후 TODO.
class PerformanceE2ETest : AbstractE2ETest() {

    @Test
    fun `보안 PR 시나리오의 webhook 수신 → PR 코멘트 등록까지 elapsed 가 60s 이내`() {
        val fixture = SecurityPrFixture.load()
        WireMockScenarios.stubAll(wireMock, fixture)

        val startNanos = System.nanoTime()

        client.post()
            .uri("/api/github/webhook")
            .header("X-Hub-Signature-256", fixture.webhookSignature)
            .header("X-GitHub-Event", "pull_request")
            .header("Content-Type", "application/json")
            .body(fixture.webhookPayload)
            .exchange()
            .expectStatus().isAccepted

        // 종착점 = PR review POST 호출 도달 (E2EAssertions 의 동일 검증 메커니즘)
        E2EAssertions.assertPrReviewSubmitted(wireMock, fixture.prNumber)

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

        // stdout 출력 — docs/operations/e2e-elapsed-baseline.md 의 측정값 갱신용
        println("[performance] webhook → PR review elapsed = ${elapsedMs}ms")

        assertThat(elapsedMs)
            .withFailMessage("elapsed=%dms 가 60s 목표 초과", elapsedMs)
            .isLessThan(60_000)
    }
}
