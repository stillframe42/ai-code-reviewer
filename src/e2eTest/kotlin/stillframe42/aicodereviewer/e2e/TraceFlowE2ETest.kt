package stillframe42.aicodereviewer.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.e2e.support.SecurityPrFixture
import stillframe42.aicodereviewer.e2e.support.TempoQuery
import stillframe42.aicodereviewer.e2e.support.WireMockScenarios
import java.time.Duration

// 분산 trace 종단 검증 — 보안 PR 1건 처리 후 Spring Boot 와 Python 에이전트의 span 이
// Tempo 의 단일 trace 로 연결되는지 확인한다. W3C traceparent 전파가 종단까지 작동한다는 증거.
class TraceFlowE2ETest : AbstractE2ETest() {

    @Test
    fun `보안 PR 처리 시 두 서비스 span 이 단일 trace 로 연결된다`() {
        val fixture = SecurityPrFixture.load()
        WireMockScenarios.stubAll(wireMock, fixture)

        client.post()
            .uri("/api/github/webhook")
            .header("X-Hub-Signature-256", fixture.webhookSignature)
            .header("X-GitHub-Event", "pull_request")
            .header("Content-Type", "application/json")
            .body(fixture.webhookPayload)
            .exchange()
            .expectStatus().isAccepted

        // span export(BatchSpanProcessor flush) + Tempo 수집 지연을 감안해 폴링 조회.
        val tempoQuery = TempoQuery(tempoHttpPort)
        await atMost Duration.ofSeconds(60) untilAsserted {
            assertThat(tempoQuery.findTraceLinkingBothServices())
                .withFailMessage("Tempo 에 ai-code-reviewer + ai-agent-service span 을 모두 가진 trace 가 없음")
                .isNotNull
        }
    }
}
