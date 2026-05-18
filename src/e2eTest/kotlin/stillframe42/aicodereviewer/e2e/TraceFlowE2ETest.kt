package stillframe42.aicodereviewer.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.e2e.support.SecurityPrFixture
import stillframe42.aicodereviewer.e2e.support.TraceAssertions
import stillframe42.aicodereviewer.e2e.support.WireMockScenarios
import java.time.Duration

// 분산 trace propagation 흐름의 사전 점검. 본격 검증 (Langfuse UI 단일 trace 시각화 등) 은 후속 작업.
// 현 시점에서는 Spring Boot → Remote 에이전트 호출에 trace 헤더가 주입되지 않음을 박제.
class TraceFlowE2ETest : AbstractE2ETest() {

    // 사전 점검: 현 상태가 미구현임을 정량적으로 박제.
    // 향후 fixup 진행 시 마커가 1+ 발견되면 본 테스트는 실패 → @Disabled 테스트 enable 트리거.
    @Test
    fun `현 상태 박제 — Remote agent stdout 에 trace 헤더 흔적 0건`() {
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

        // 보안 PR 흐름이 한 차례 흘러갈 시간 — agent_node 마커가 등장하면 흐름 진행 신호.
        await atMost Duration.ofSeconds(30) untilAsserted {
            val flowStarted = remoteAgentLogs.snapshot().any {
                it.contains("agent_node") || it.contains("POST /agent/analyze")
            }
            assertThat(flowStarted)
                .withFailMessage("시나리오 흐름 미진행 — 인프라 회귀 가능성 (사전 점검 의미 없음)")
                .isTrue
        }

        // 현 상태 박제 — Remote agent stdout 에 trace 마커 0건
        val markerCount = TraceAssertions.countTraceMarkers(remoteAgentLogs)
        assertThat(markerCount)
            .withFailMessage(
                "Remote agent stdout 에 trace 마커 %d건 발견 — fixup 이 일부 진행됐을 가능성. @Disabled 테스트 enable 검토.",
                markerCount,
            )
            .isEqualTo(0)
    }

    // trace propagation fixup 후 @Disabled 제거 → 본 테스트가 검증을 인계받음.
    // fixup 항목:
    //   1. RemoteAgentConfig 의 WebClient 에 trace 헤더 주입 ExchangeFilterFunction 추가
    //   2. Remote 에이전트 (Python) 측 trace 헤더 파싱 + Langfuse SDK 통합
    @Test
    @Disabled("trace propagation fixup 후 @Disabled 제거 — WebClient interceptor + Remote agent 측 처리 선행 필요")
    fun `Spring Boot → Remote 에이전트 호출에 trace propagation 헤더 포함`() {
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

        await atMost Duration.ofSeconds(30) untilAsserted {
            val markerCount = TraceAssertions.countTraceMarkers(remoteAgentLogs)
            assertThat(markerCount)
                .withFailMessage("Remote agent stdout 에서 trace 헤더 흔적 없음")
                .isGreaterThanOrEqualTo(1)
        }
    }
}
