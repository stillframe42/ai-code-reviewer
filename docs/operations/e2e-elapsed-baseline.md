# E2E 처리 시간 baseline 노트

## 측정 환경

- 측정 시점: 2026-05-18
- 측정 클래스: `PerformanceE2ETest` (1회 측정)
- 환경: WireMock stub (실 LLM 호출 없음) + Testcontainers (PostgreSQL pgvector + Redis + Remote agent 컨테이너)
- 시나리오: `SecurityPrFixture` (`SecurityConfig.kt` 신규 + plaintext password 패턴 diff)
- 측정 구간: `webhook POST` 시점 → `PR review POST` WireMock verify 통과 시점

## 전체 elapsed

webhook 수신 → PR review POST 도달까지: **1177ms** (1회 측정, 2026-05-18 실 실행 기준)
60s 목표: 충분히 안 (E2E stub 환경 기준, 목표의 약 2% 수준)

## Segment 별 시간 소비 영역 (시나리오 1 자연 실행 기준 추정)

| Segment | 추정 소요 | 비중 |
|---|---|---|
| Webhook 수신 + HMAC-SHA256 서명 검증 | ~10ms | 미미 |
| GitHub PR diff/files 조회 (WireMock 스텁) | ~50ms | 미미 |
| RAG `buildContextIds` (OpenAI 임베딩 stub) | ~50ms | 미미 |
| Remote 에이전트 호출 (`POST /agent/analyze`) | ~100ms | 작음 |
| Remote 에이전트 LangGraph 실행 (stub LLM 즉시 응답, 3+ chat 호출) | ~500ms | 가장 큼 |
| Spring Boot polling (`interval=500ms` × 2 attempts) | ~535ms | 가장 큼 |
| `AgentFindingMapper.toCodeReview()` | ~10ms | 미미 |
| `DiffPositionResolver` + PR review 등록 | ~50ms | 미미 |

(이전 실 실행의 `polling done: attempts=2, elapsed=535ms` 로그가 polling 구간의 정량 근거)

## 운영 환경 vs E2E 환경 차이

- **E2E (현재)**: LLM stub 즉시 응답 → 전체 ~1-2초
- **운영 (실 LLM)**: chat completion 1건당 3-10초 × 3 호출 = ~10-30초 + polling overhead → 추정 ~15-40초
- **60s 목표**: 운영 기준 충분히 달성 가능 (LLM 응답 시간이 dominant, polling overhead 는 ms 수준)

## 30초/45초/60초 segment 별 시간 소비 (운영 기준 추정)

| 시간대 | 가장 큰 비중 영역 | 비고 |
|---|---|---|
| 0~30초 | LLM 호출 (agent_node + final answer) | 운영의 *대부분* |
| 30~45초 | LLM 호출 + extract_issues + polling | LangGraph 호출 수가 늘어나는 구간 |
| 45~60초 | polling + GitHub API + DB 저장 | 60초 임박 시 timeout 위험 영역 |

## 향후 측정 (후속 작업의 책임)

- 10회 반복 실행 → p50/p95/max 정량 측정 (statistical baseline)
- 시나리오 2 (일반 PR → Spring AI 직접 경로) baseline 측정 — 비교군
- 폴링 비효율 측정: 마지막 IN_PROGRESS 응답 시점 ~ DONE 응답 시점 사이 대기 (Kafka 콜백 전환 정량 근거)
- 운영 환경 실 LLM 호출 baseline (별도 환경, E2E 와 분리)
