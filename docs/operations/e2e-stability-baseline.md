# E2E 안정성 baseline

## 5회 반복 결과 (2026-05-18)

| 회차 | 결과 | 소요 시간 |
|---|---|---|
| 1 | PASS | 31s |
| 2 | PASS | 27s |
| 3 | PASS | 27s |
| 4 | PASS | 26s |
| 5 | PASS | 26s |

**5/5 통과 — flakiness 0.** 첫 회차만 JVM/컨테이너 warm-up 으로 5초 길고 이후 안정.

## 인벤토리 (1줄 메모)

신규: GeneralPrE2ETest, AgentDownE2ETest, GeneralPrFixture, AgentDownPrFixture, fixture 8개 (webhook 2 / diff 2 / pr-files 2 / anthropic 2).
변경: WireMockScenarios (+stubAllForGeneral, +stubAllForAgentDown, +stubAgentConnectionReset, +helper), E2EAssertions (+assertRemoteAgentNotCalled, +assertFallbackMetricIncremented, +assertRemoteAgentHealthDown, +assertFallbackMetricExposedAsPrometheus, +fallbackMetricBaseline), AbstractE2ETest (+processedEvent cleanup, +agent.remote.url System property hook), docs/operations/agent-fallback.md (+absolute rate 보조 알람).
정리: 코드/fixture/주석의 plans 일정 메타 일괄 제거 (이전 자산 포함).

## cross-check 결과 (1줄)

전체 e2eTest sequential 실행이 격리 보강 (setUpBase 의 processedEvent cleanup) 으로 안정 통과 — 같은 PR fixture 공유로 인한 `skipIfAlreadyProcessed` 우회가 본질이었음.

## Prometheus 메트릭 노출 확정 (1줄)

`/actuator/prometheus` 응답에 `agent_fallback_count_total{reason="unavailable"}` 라인 노출 박제 (`AgentDownE2ETest` 의 `assertFallbackMetricExposedAsPrometheus`). 운영 PromQL 룰은 `docs/operations/agent-fallback.md` 의 ratio + absolute rate 두 가지.
