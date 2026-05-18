# Remote Agent 폴백 운영 가이드

## 요약

원격 에이전트(Deep Security Analysis)가 다운되거나 느려질 때 Spring AI 기본 리뷰 경로로
graceful degradation 한다. `sealed AgentException` 계층 (unavailable / timeout / failed) 으로
분류하고, 분류되지 않은 transient 예외는 `error` reason 으로 동일하게 폴백한다.
폴백 발생은 `agent.fallback.count{reason}` Micrometer Counter 로 reason 태그별로 추적된다.

## 폴백 트리거

| reason | 트리거 조건 | 매핑 위치 |
|---|---|---|
| `unavailable` | connect refused, 5xx 응답 | `RemoteAgentClient.mapHttpExceptions` |
| `timeout` | wall-clock 초과 또는 maxAttempts 도달 | `AgentPoller.pollUntilComplete` |
| `failed` | agent 응답 `status=FAILED` | `AgentPoller.pollUntilComplete` |
| `error` | 그 외 transient 예외 (RAG 임베딩 실패 등) | `DefaultGitHubWebhookService.runAiReview` 의 `catch (Exception)` 안전망 |

4xx 응답은 호출자(우리) 버그 신호이므로 매핑하지 않고 그대로 전파된다 (현재는 상위 `catch (Exception)` 가 흡수해 `error` 로 분류 — 의도된 동작이지만 향후 별도 reason 분리를 검토할 수 있음).

## 메트릭 키

- 메인: `agent.fallback.count{reason}` (Micrometer `Counter`)
- reason 값: `unavailable` | `timeout` | `failed` | `error` (4종 고정)
- 수집 경로: `GET /actuator/prometheus` (관리 포트 9001)
- 헬스: `GET /actuator/health/remote-agent` (별도 group endpoint)

Grafana 알람과 dashboard 는 이벤트 큐 도입 후 본격 구성한다. 현재는 위 메트릭 키만
박제 — Prometheus 가 수집은 하지만 알람 rule 은 등록하지 않은 상태.

## 권장 알람 임계치

5분 내 폴백 비율이 전체 webhook 의 30% 이상이면 운영 알람을 발생시킨다.

```promql
rate(agent_fallback_count_total[5m])
  / rate(github_webhook_received_total[5m]) > 0.3
```

(Prometheus 노출 시 Micrometer 가 `.` 을 `_` 으로, `Counter` 에 `_total` 접미사를 자동 추가한다.)

**보조 알람 (단순 절대값)**: 5분 내 폴백 호출이 분당 0.5건 이상이면 알람.

```promql
rate(agent_fallback_count_total[5m]) > 0.5
```

ratio 알람보다 단순하나 webhook 트래픽 변동에 둔감 — 트래픽이 적은 시간대(야간 등) 의 폴백을 더 잘 잡는다. 운영 트래픽 패턴에 따라 ratio / absolute 둘 중 또는 둘 다 등록 검토.

## 헬스 다운 시 점검 체크리스트

1. **헬스 엔드포인트 확인** — `curl -s http://<host>:9001/actuator/health/remote-agent` 의 status 가 `DOWN` 인지 확인.
2. **에이전트 컨테이너 상태** — 컨테이너 로그·프로세스 확인. OOM, 패닉 종료 여부 점검.
3. **네트워크 경로** — DNS, 방화벽, 컨테이너 네트워크 (compose / k8s service) 확인.
4. **메트릭 `reason` 분포** — `agent.fallback.count{reason=unavailable}` 우세면 (1)→(2)→(3) 순, `timeout` 우세면 agent 처리량/응답 시간 점검.

## 수동 검증 절차 (확인 기준)

### A. 컨테이너 종료 시 폴백 코멘트 등록

1. `docker compose stop python-agent` 로 에이전트 종료.
2. 보안 파일 (예: `SecurityConfig.kt`) 을 포함하는 PR webhook 을 전송.
3. GitHub PR 페이지에서 **일반 Spring AI 리뷰 코멘트가 등록** 되었는지 확인 — agent 심층 분석 마커 (`"에이전트 심층 분석 결과"`) 가 없어야 한다.
4. 메트릭 확인: `agent.fallback.count{reason=unavailable}` 카운터가 1 증가했는지 (`/actuator/prometheus` 또는 `/actuator/metrics/agent.fallback.count?tag=reason:unavailable`).

### B. 헬스 다운 표시 확인

에이전트 종료 상태에서:

```bash
curl -s http://localhost:9001/actuator/health/remote-agent | jq
```

응답이 다음 형태인지 확인:

```json
{"status":"DOWN"}
```

(`RemoteAgentClient.checkHealth()` 가 현재 `Boolean` 만 반환하므로 `details` 는 비어 있다. `error` details 포함은 `port.checkHealth()` 시그니처 확장 후속 작업으로 분리.)
