# ai-code-reviewer

[![CI](https://github.com/stillframe42/ai-code-reviewer/actions/workflows/ci.yml/badge.svg)](https://github.com/stillframe42/ai-code-reviewer/actions/workflows/ci.yml)

> Spring Boot 4.0.3 + Kotlin + Spring AI 기반의 AI 코드 리뷰 자동화 시스템

---

## 시스템 개요

`ai-code-reviewer`는 GitHub Pull Request 이벤트를 자동으로 감지해 AI가 코드를 분석하고, 인라인 리뷰 코멘트를 작성하는 코드 리뷰 자동화 시스템입니다. 보안 변경이 포함된 PR은 별도 Python 에이전트(LangGraph)로 위임해 더 깊은 분석을 수행합니다.

### 핵심 기능

- **GitHub Webhook 통합**: PR 이벤트(opened, synchronize, reopened) 수신 → AI 리뷰 자동 실행 → 인라인 코멘트 작성
- **이중 리뷰 경로**: `SecurityFileDetector` 가 파일 경로 카테고리를 분석해 분기
  - 보안 파일 포함 PR → **Python LangGraph 에이전트** (`ai-agent-service`, 별도 저장소) 로 위임
  - 일반 PR → **Spring AI 직접 호출** (Anthropic/OpenAI)
  - 에이전트 실패 시 Spring AI 로 fallback (메트릭 분리 추적)
- **RAG 기반 컨벤션 리뷰**: 프로젝트 코딩 컨벤션 문서를 벡터·키워드 하이브리드 검색(RRF)으로 조회하고, LLM 컨텍스트 압축 후 리뷰에 반영. multi-query retrieval 로 검색 다양성 확보
- **diff 전처리**: 테스트 파일·잠금 파일 자동 제거, context 줄 수 조정, 토큰 한도 초과 시 변경량 적은 청크 우선 제거로 토큰 절감
- **Tool Calling 리뷰**: GitHub API를 도구로 활용해 파일별 상세 정보를 조회하는 고급 리뷰 모드
- **AI 채팅**: 단일 응답 및 SSE 스트리밍 방식으로 자유 형식 AI 대화 지원
- **다양한 AI 백엔드**: Anthropic Claude / OpenAI GPT 프로바이더 선택 지원
- **리뷰 이력 관리**: 리뷰 결과 PostgreSQL 저장, PR별·통계 조회 API 제공
- **통합 옵저버빌리티**:
  - **Langfuse**: Spring Boot LLM 호출 + Python 에이전트 LangGraph 호출이 단일 세션 trace 로 통합 (sessionId 전파)
  - **Grafana Tempo**: W3C `traceparent` 헤더 자동 전파로 두 서비스 span 이 한 trace 로 연결
  - **Prometheus / Micrometer**: 메트릭 (캐시 hit/miss, fallback reason, 토큰 비용 등)

### 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Kotlin 2.2.21 |
| 프레임워크 | Spring Boot 4.0.3 |
| AI 통합 | Spring AI 2.0.0-M2 |
| 외부 에이전트 | Python `ai-agent-service` (LangGraph, 별도 저장소) |
| 동시성 | JDK 21 가상 스레드 (virtual threads) |
| 빌드 도구 | Gradle (Kotlin DSL) |
| JDK | JDK 21 |
| DB | PostgreSQL (운영·테스트, Testcontainers) |
| DB 마이그레이션 | Flyway 10+ |
| 캐시 | Redis |
| 벡터 DB | PgVector (PostgreSQL 확장, HNSW 인덱스) |
| RAG 검색 | 벡터 + 키워드 하이브리드 (RRF), multi-query, LLM 컨텍스트 압축 |
| LLM Observability | Langfuse (자체 호스팅) |
| 분산 Trace | Grafana Tempo + OpenTelemetry / Micrometer Tracing |
| 메트릭 | Micrometer / Prometheus / Grafana |
| 기본 모델 | claude-haiku-4-5-20251001 (일반) / 보안은 GPT-4o-mini 기반 에이전트 |

---

## 아키텍처

### 헥사고날 (Ports & Adapters)

각 기능 패키지가 `domain` / `application` / `adapter` 3 계층 구조를 따릅니다.

| 계층 | 역할 | 예시 |
|------|------|------|
| `domain/model` | 순수 도메인 모델 (외부 의존 없음) | `CodeReview`, `CodeIssue`, `RagDocument` |
| `domain/port/in` | 인바운드 포트 — UseCase 인터페이스 | `ReviewUseCase`, `AgentReviewUseCase`, `GitHubWebhookUseCase` |
| `domain/port/out` | 아웃바운드 포트 — 외부 시스템 추상화 | `AiReviewPort`, `AgentAnalysisPort`, `ConventionVectorPort`, `MultiQueryGeneratorPort` |
| `domain/service` | 도메인 서비스 (순수 함수 또는 properties 의존 빈) | `SecurityFileDetector`, `DiffPreprocessor` (object), `PrImportanceAnalyzer` (@Component) |
| `application` | UseCase 구현체 — 포트를 조합 | `DefaultReviewService`, `DefaultAgentReviewService` |
| `adapter/in/web` | HTTP 컨트롤러 | `ReviewController`, `WebhookController`, `AgentCallbackController` |
| `adapter/out/{ai,remote,persistence,cache,...}` | 외부 시스템 어댑터 | `ReviewAdapter` (Spring AI), `RemoteAgentClient` (Python), `RedisReviewCacheAdapter` |

도메인 측에 Spring AI `Document` leak 차단을 위해 `RagDocument` 도메인 모델을 별도로 정의하고, 어댑터 경계에서 `RagDocumentMapper` 로 변환합니다.

자세한 아키텍처 다이어그램은 [docs/architecture.md](docs/architecture.md)를 참조하세요.

---

## 로컬 실행 방법

### 사전 요구사항

- JDK 21
- Gradle 8.x (또는 `./gradlew` Wrapper)
- Docker Desktop (PostgreSQL / Redis / Langfuse / ai-agent-service 컨테이너용)
- LLM API 키 (Anthropic 또는 OpenAI)
- `ai-agent-service` 저장소 (보안 경로 사용 시 형제 디렉토리에 클론)

### 1. 저장소 클론

```bash
# 본 저장소
git clone https://github.com/stillframe42/ai-code-reviewer.git
cd ai-code-reviewer

# 형제 저장소 (선택 — 보안 경로 사용 시)
cd ..
git clone <ai-agent-service repo url> ai-agent-service
cd ai-code-reviewer
```

### 2. `.env` 파일 작성

`.env.example` 을 복사해 시작합니다.

```bash
cp .env.example .env
```

`.env` 의 주요 변수:

| 변수 | 설명 |
|------|------|
| `NEXTAUTH_SECRET` / `SALT` | Langfuse self-host 용 |
| `GF_SECURITY_ADMIN_PASSWORD` | Grafana admin 패스워드 |
| `INTERNAL_AGENT_CALLBACK_TOKEN` | Spring Boot ↔ Python ai-agent-service 공유 비밀 (`openssl rand -hex 32`) |
| `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` / `LANGFUSE_HOST` | Python 측 Langfuse 콜백 — 통합 세션 trace 활성화 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Python OTel → Tempo collector. 미설정 시 분산 trace 비활성 |

### 3. 컨테이너 기동

```bash
# 핵심 인프라 (postgres, redis, langfuse-server, ai-agent-service)
docker compose up -d

# 옵션: 모니터링 stack (grafana, prometheus, tempo)
docker compose -f docker-compose.monitoring.yml up -d
```

기본 포트:

| 컴포넌트 | 포트 |
|---------|------|
| PostgreSQL | `15432` |
| Redis | `16379` |
| Langfuse UI | `3000` |
| ai-agent-service | `8081` |
| Grafana | `3001` |
| Prometheus | `9090` |
| Tempo | `3200` / `4317` (gRPC) / `4318` (HTTP OTLP) |

### 4. API 키 설정 — `application-secret.yml`

`src/main/resources/application-secret.yml` 파일을 생성하고 API 키 + 비밀을 설정합니다. **이 파일은 `.gitignore` 에 등록되어 있어 커밋되지 않습니다.**

```yaml
spring:
  datasource:
    password: aireviewer

anthropic:
  api-key: sk-ant-...

openai:
  api-key: sk-...

github:
  app:
    webhook-secret: <openssl rand -hex 32 결과>

langfuse:
  public-key: pk-lf-...
  secret-key: sk-lf-...

agent:
  remote:
    callback:
      internal-auth-token: <.env 의 INTERNAL_AGENT_CALLBACK_TOKEN 과 동일 값>
```

YAML 주의: `key: value` 의 콜론 뒤 **공백 필수**. `key:value` 는 SnakeYAML 이 silent 하게 무시하고 `${VAR:default}` placeholder 의 빈 default 로 fallback 합니다 (인증 401 같은 silent fail 의 흔한 원인).

### 5. 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

### 6. 동작 확인

```bash
# Spring Boot 헬스 체크
curl http://localhost:8080/actuator/health

# 메트릭 (Prometheus 형식)
curl http://localhost:8080/actuator/prometheus
```

---

## GitHub App 설정 (Webhook 자동화)

GitHub PR 이벤트를 수신하고 자동 리뷰를 실행하려면 GitHub App 등록이 필요합니다.

### `application-secret.yml` 추가 설정

```yaml
github:
  app:
    app-id: <GitHub App ID>
    private-key-path: <RSA 개인키 파일 경로 (.pem)>
    webhook-secret: <GitHub App Webhook Secret>
```

### Webhook 이벤트 처리 흐름

1. **수신**: GitHub PR 이벤트 → `POST /api/github/webhook`
2. **검증**: HMAC-SHA256 timing-safe 서명 검증 (`MessageDigest.isEqual`) — 빈 secret 일 때는 모든 요청 401
3. **중복 차단**: `processed_pull_request_event` 테이블 기준 (repository + PR 번호 + head SHA)
4. **diff 조회 및 전처리**: 테스트/잠금 파일 제거, 토큰 한도 초과 시 변경량 적은 청크 우선 제거
5. **라우팅 분기** (`SecurityFileDetector.hasSecurityFile`):
   - **보안 파일 포함** → `agentReviewUseCase.review(...)` (Python 에이전트 호출)
     - `POST /agent/analyze` → analysis_id 발급 → `GET /agent/analyze/{id}` 폴링
     - `AgentException` 발생 시 Spring AI 로 fallback (메트릭 `agent_fallback_reason` 분리)
   - **일반 파일만** → `reviewUseCase.review(...)` (Spring AI 직접 호출)
     - PR 중요도 분석 → 모델 선택 (`AiModelSelector`)
     - RAG 컨벤션 검색 (벡터+키워드 RRF → multi-query → LLM 압축) → 컨텍스트 주입
     - 캐시 조회 (`ReviewCachePort`, hit/miss 메트릭)
6. **저장**: `review_results`, `tool_call_logs`, `llm_cost_logs`
7. **인라인 코멘트**: `DiffPositionResolver` 가 issue.line → diff position 매핑 → GitHub PR Reviews API

---

## API 사용 예시

### POST /api/review — 코드 리뷰

```bash
curl -X POST http://localhost:8080/api/review \
  -H "Content-Type: application/json" \
  -d '{
    "code": "fun getUserById(id: String) = db.query(\"SELECT * FROM users WHERE id = \" + id)",
    "provider": "ANTHROPIC",
    "diffOptions": {
      "filterTestFiles": true,
      "filterLockFiles": true,
      "contextLines": 3
    }
  }'
```

```json
{
  "overallScore": 3,
  "summary": "CRITICAL 보안 이슈 1건이 발견되었습니다.",
  "issues": [
    {
      "id": "issue-1",
      "category": "SECURITY",
      "severity": "CRITICAL",
      "filename": "UserRepository.kt",
      "line": 1,
      "description": "문자열 연결로 SQL을 구성하면 SQL Injection 공격에 노출됩니다.",
      "suggestion": "PreparedStatement 또는 파라미터화된 쿼리를 사용하세요."
    }
  ],
  "positives": []
}
```

#### Tool Calling 모드 사용

GitHub API를 도구로 활용해 파일별 상세 정보를 조회하는 고급 리뷰 모드입니다.

```bash
curl -X POST http://localhost:8080/api/review \
  -H "Content-Type: application/json" \
  -d '{
    "code": "<git diff 문자열>",
    "provider": "ANTHROPIC",
    "reviewMode": "WITH_TOOLS",
    "installationId": 12345678
  }'
```

#### `diffOptions` 파라미터

`diffOptions`를 생략하면 전처리 없이 raw 코드가 AI에 전달됩니다.

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `filterTestFiles` | boolean | `true` | `*Test.kt`, `*Spec.kt` 등 테스트 파일 제외 |
| `filterLockFiles` | boolean | `true` | `*.lock`, `package-lock.json` 등 잠금 파일 제외 |
| `additionalExcludePatterns` | string[] | `[]` | 추가 제외 glob 패턴 목록 |
| `contextLines` | integer | `3` | 변경 전후 유지할 context 줄 수 (0 = +/- 줄만) |
| `maxTokens` | integer? | `null` | 최대 허용 토큰 수 — 초과 시 변경량이 적은 청크부터 제거 |
| `strategyOverrides` | Map | `{}` | 파일별 리뷰 전략 오버라이드 (glob 패턴 → `FileReviewStrategy`) |

#### `FileReviewStrategy` 값

| 값 | 설명 | 기본 적용 확장자 |
|----|------|----------------|
| `FullReview` | 코드 품질 전체 리뷰 | `.kt`, `.java`, `.ts`, `.py` 등 |
| `QueryReview` | 변경 의도·설정값 위주 리뷰 | `.yml`, `.sql`, `.json`, `Dockerfile` 등 |
| `Skip` | 리뷰 대상에서 완전히 제외 | `.png`, `.jar`, `gradlew` 등 |

#### 이슈 카테고리 / 심각도

| 카테고리 | 설명 |
|----|------|
| `PERFORMANCE` | 성능 문제 (N+1 쿼리, 비효율 루프 등) |
| `SECURITY` | 보안 취약점 (SQL Injection, 하드코딩 자격증명 등) |
| `READABILITY` | 가독성 문제 (매직넘버, 불명확한 변수명 등) |
| `ARCHITECTURE` | 설계 문제 (SRP 위반, 의존성 방향 오류 등) |

| 심각도 | 설명 |
|----|------|
| `CRITICAL` | 즉시 수정 필요 (보안 취약점, 런타임 오류 등) |
| `MAJOR` | 중요 개선 필요 (성능 병목, 설계 결함 등) |
| `MINOR` | 선택적 개선 (코드 스타일, 사소한 최적화 등) |
| `SUGGESTION` | 제안 사항 (선택 사항) |

### GET /api/reviews/{owner}/{repo}/{prNumber} — PR 리뷰 조회

```bash
curl http://localhost:8080/api/reviews/my-org/my-repo/42
```

```json
{
  "repoFullName": "my-org/my-repo",
  "prNumber": 42,
  "headSha": "abc1234",
  "status": "DONE",
  "createdAt": "2026-04-02T10:00:00",
  "completedAt": "2026-04-02T10:00:15",
  "summary": "전반적으로 코드 품질이 양호합니다.",
  "issueCount": 2,
  "toolCallCount": 3,
  "modelName": "claude-haiku-4-5-20251001"
}
```

### GET /api/reviews/stats — 리뷰 통계

```bash
curl http://localhost:8080/api/reviews/stats
```

```json
{
  "totalReviews": 128,
  "categoryDistribution": { "SECURITY": 15, "PERFORMANCE": 32, "READABILITY": 48, "ARCHITECTURE": 33 },
  "averageToolCallCount": 2.4,
  "costByModel": { "claude-haiku-4-5-20251001": 0.0142 },
  "cacheHitRate": 0.35,
  "estimatedSavings": 0.0051
}
```

---

## 내부 endpoint (X-Internal-Auth 가드)

`/api/rag/context/{contextId}` 와 `/internal/*` 경로는 모두 `X-Internal-Auth` 헤더 토큰 검증(`InternalAuthValidator`, timing-safe 비교)을 통과해야 호출됩니다. 토큰은 `INTERNAL_AGENT_CALLBACK_TOKEN` 환경변수 + `application-secret.yml` 양쪽에 같은 값으로 설정합니다.

| Endpoint | 용도 | 호출자 |
|----------|------|-------|
| `GET /api/rag/context/{contextId}` | RAG 컨벤션 컨텍스트 lazy fetch | Python 에이전트의 `get_convention_context` tool |
| `POST /internal/agent/callback` | 에이전트 비동기 분석 결과 push (현재 골격) | (미사용 — Kafka 도입 전까지 골격 유지) |
| `POST /internal/conventions/reindex` | 컨벤션 RAG 인덱스 재구축 | 운영자 (수동) |

빈 토큰 = 모든 요청 401 (config 누락 = 사실상 endpoint 비활성).

---

## 데이터베이스 스키마

Flyway로 마이그레이션을 관리합니다. `src/main/resources/db/migration/` 에 V1~V11 의 SQL 이 있습니다.

| 테이블 | 설명 |
|--------|------|
| `processed_pull_request_event` | 처리된 Webhook 이벤트 기록 (중복 처리 방지). 유니크 제약 `ux_processed_event` |
| `review_requests` | 리뷰 요청 상태 추적 (PENDING → PROCESSING → DONE / FAILED) |
| `review_results` | AI 리뷰 결과 (요약, 이슈 목록 JSON, 모델명) |
| `tool_call_logs` | Tool Calling 호출 이력 (도구명, 인자, 응답 크기, 소요 시간) |
| `review_issue_categories` | 이슈 카테고리 집계용 정규화 테이블 |
| `llm_cost_logs` | LLM 호출별 비용 기록 (모델명, 프롬프트·완성 토큰, 추정 비용) |
| `vector_store` | RAG 컨벤션 문서 벡터 저장소 (PgVector, **HNSW 인덱스** `ix_vector_store_embedding`, **tsvector** GIN 인덱스 `ix_vector_store_tsv` simple dictionary) |

인덱스 명명 규칙: `ux_*` (unique), `ix_*` (일반), `pk_*` (primary key).

마이그레이션 주요 이력:
- V1: processed_event 테이블
- V2: review_requests / review_results / tool_call_logs
- V3: tool_call_count + issue_categories
- V4: llm_cost_logs
- V5–V7: code_conventions → vector_store rename
- V8: vector_store id → UUID
- V9: content_tsv 컬럼 추가
- V10: 인덱스명 표준화 (`idx_*` → `ix_*`)
- V11: tsvector dictionary 를 `simple` 로 변경

---

## 프롬프트 버전

현재 활성 버전: **v13** (`application-ai.yml` 의 `review-system: classpath:prompts/review/review-system-v13.st`)

| 버전 | 주요 변경 |
|------|---------|
| v1 | 초기: 단순 역할 정의 |
| v2 | few-shot 예시, 카테고리·심각도 정의 추가 |
| v3 | 토큰 최적화 (메타 설명 제거) |
| v4 | v1 기반 재정립 (필수 스키마만 유지) |
| v5 | `[QUERY_REVIEW]` 헤더 파일 처리 지침 추가 |
| v6 | `filename` 필드 안내 (diff position 매핑용) |
| v7 | summary 2문장 제한, positives 최대 3개 제한 |
| v8 | Tool Calling 사용 지침 추가 |
| v9 | RAG 컨벤션 컨텍스트 주입 지침 추가 |
| v10 | BeanOutputConverter format 스키마 주입 |
| v11~v12 | 실험적 변형 |
| **v13** | **현재**: v11 기반 + Few-shot 3 예시 (C-2 채택, 2026-04-24) |

버전별 상세 변경 이력은 `src/main/resources/prompts/README.md` 참조.

---

## 프로젝트 구조

7개 기능 패키지가 모두 동일한 헥사고날 layer 패턴 (domain / application / adapter) 을 따릅니다.

```
src/main/kotlin/stillframe42/aicodereviewer/
├── AiCodeReviewerApplication.kt
├── core/
│   └── AiProvider.kt                  # ANTHROPIC, OPENAI 열거형
├── config/                            # 전역 빈 설정
│   ├── AiClientConfig.kt / AdvisorConfig.kt / ReviewConfig.kt
│   ├── GitHubConfig.kt / RedisConfig.kt / JacksonConfig.kt
│   ├── LangfuseObservationConfig.kt / ToolObservationConfig.kt
│   ├── RemoteAgentConfig.kt           # WebClient + ObservationRegistry (traceparent 자동 주입)
│   └── *Properties.kt                 # @ConfigurationProperties (Ai/GitHub/Langfuse/Llm/Remote/Rag/Review/...)
├── common/                            # 기능 횡단 공통 컴포넌트
│   ├── GlobalExceptionHandler.kt / AiPromptBuilder.kt / TokenEstimator.kt / Logging.kt
│   ├── auth/InternalAuthValidator.kt  # X-Internal-Auth timing-safe 검증
│   ├── exception/NotFoundException.kt
│   ├── advisor/                       # Spring AI Advisor: Cost/Logging/Retry
│   ├── cache/AbstractRedisCacheAdapter.kt
│   ├── langfuse/                      # Langfuse REST 클라이언트 (기술 종속)
│   ├── metrics/                       # Micrometer 메트릭 + 이벤트
│   ├── observability/                 # 기술 중립 관측 포트 (ObservabilityPort, WithSpan)
│   └── port/CostLogPort.kt
├── review/                            # 코드 리뷰 기능
│   ├── domain/
│   │   ├── model/                     # CodeReview, CodeIssue, DiffFilterOptions, PrImportance 등
│   │   ├── service/                   # DiffPreprocessor(object), PrImportanceAnalyzer, AiModelSelector, FileExtensionClassifier
│   │   └── port/
│   │       ├── in/{ReviewUseCase, ReviewQueryUseCase, ReviewSummaryResult}.kt
│   │       └── out/{AiReviewPort, ReviewPersistencePort, ReviewQueryPort, ReviewCachePort, ReviewCacheStatsPort, ...}.kt
│   ├── application/{DefaultReviewService, DefaultReviewQueryService, ClaimVerifier}.kt
│   └── adapter/
│       ├── in/web/{ReviewController, ReviewQueryController}.kt
│       └── out/
│           ├── ai/{ReviewAdapter, LangfuseToolSpanAdapter, NoopToolObservationAdapter, tool/GitHubTools}.kt
│           ├── cache/{RedisReviewCacheAdapter, RedisReviewCacheStatsAdapter, MeteredReviewCacheAdapter}.kt
│           └── persistence/{ReviewPersistenceAdapter, ReviewQueryAdapter, CostLogAdapter, entity/}.kt
├── rag/                               # RAG 컨벤션 검색
│   ├── domain/
│   │   ├── model/{ConventionCategory, RagDocument}.kt   # RagDocument: Spring AI Document leak 차단용
│   │   ├── service/{FileCategoryMapper, ReciprocaRankFusion}.kt
│   │   └── port/
│   │       ├── in/{ConventionIndexUseCase, GetRagContextUseCase}.kt
│   │       └── out/{ConventionVectorPort, ConventionKeywordSearchPort, ContextCompressorPort, MultiQueryGeneratorPort, DocumentPreparerPort}.kt
│   ├── application/{HybridConventionSearchService, ConventionContextService, DefaultConventionIndexService, DefaultGetRagContextService}.kt
│   └── adapter/
│       ├── in/{web/{RagContextController, ConventionAdminController}, cli/ConventionIndexingRunner}.kt
│       └── out/
│           ├── ai/{ConventionVectorAdapter, LlmContextCompressorAdapter, LlmMultiQueryGeneratorAdapter, MarkdownHeaderSplitter, OverlappingTokenSplitter, RagDocumentMapper}.kt
│           └── db/JdbcConventionKeywordAdapter.kt   # PostgreSQL Full-text Search
├── agent/                             # Python 에이전트 원격 호출 (보안 경로)
│   ├── domain/
│   │   ├── model/{AgentAnalysisCommand, AgentAnalysisResult, AgentCallbackResult, AgentFinding}.kt
│   │   ├── exception/{AgentException, AgentAnalysisTimeoutException, AgentAnalysisFailedException, AgentUnavailableException}.kt
│   │   ├── service/{SecurityFileDetector, AgentFindingMapper}.kt
│   │   └── port/
│   │       ├── in/{AgentReviewUseCase, AgentCallbackUseCase}.kt
│   │       └── out/AgentAnalysisPort.kt
│   ├── application/{DefaultAgentReviewService, DefaultAgentCallbackHandler, AgentPoller, AgentFallbackMetrics}.kt
│   └── adapter/
│       ├── in/web/{AgentCallbackController, dto/AgentCallbackPayload}.kt    # Jackson DTO 는 adapter 경계로 격리
│       └── out/remote/{RemoteAgentClient, RemoteAgentHealthIndicator, dto/{AgentAnalysisRequest, AgentAnalysisResponse}}.kt
├── evaluation/                        # RAG 검색 품질 평가
│   ├── domain/
│   │   ├── model/{EvaluationMetric, EvaluationResult, EvaluationScore, GoldenCase}.kt
│   │   └── port/{in/EvaluationUseCase, out/RagEvaluationPort}.kt
│   ├── application/DefaultEvaluationService.kt
│   └── adapter/out/ai/EvaluationAdapter.kt
└── github/                            # GitHub Webhook & API 통합
    ├── domain/
    │   ├── model/                     # PullRequestEvent, PrFile, PrReviewLineComment 등
    │   ├── service/DiffPositionResolver.kt   # diff line → GitHub position 매핑 (object)
    │   └── port/{in/GitHubWebhookUseCase, out/{GitHubApiPort, GitHubTokenPort, ProcessedEventPort, ReviewCommentFormatterPort}}.kt
    ├── application/DefaultGitHubWebhookService.kt
    └── adapter/
        ├── in/web/{WebhookController, HmacSignatureVerifier}.kt
        ├── out/github/{GitHubApiAdapter, GitHubAppTokenProvider, GitHubAppJwtGenerator, JwtSigner, RsaKeyLoader, client/GitHubHttpClient, ratelimit/}.kt
        ├── out/formatter/MarkdownReviewCommentFormatter.kt
        └── out/persistence/ProcessedEventAdapter.kt
```

리소스:
```
src/main/resources/
├── application.yml + application-{ai,db,github,langfuse,actuator,redis,agent}.yml
├── application-secret.yml             # API 키 (gitignore)
├── db/migration/V1~V11_*.sql          # Flyway
├── conventions/{architecture-guide, api-design, kotlin-style, security-checklist}.md
└── prompts/
    ├── review/{review-system-v1~v13, review-user, review-claim-verification}.st
    ├── rag/{context-compressor, rag-multi-query}.st
    ├── evaluation/{evaluation-context-precision, evaluation-context-recall, evaluation-answer-relevancy, evaluation-faithfulness}.st
    └── README.md                       # 버전별 변경 이력
```

테스트 source set:
```
src/test/             # 일반 단위 + 통합 — ./gradlew test 자동 실행
src/evaluationTest/   # 평가/벤치마크/실험성 — ./gradlew evaluationTest (수동, 실제 API 키 필요)
src/e2eTest/          # E2E 시나리오 (실제 Remote 에이전트 컨테이너) — ./gradlew e2eTest (수동)
```

---

## 테스트 실행 방법

### 일반 테스트

AI API 호출이 없는 단위/통합 테스트. Testcontainers 로 PostgreSQL 을 자동 실행하며, 외부 API 는 WireMock 으로 모킹합니다. Spring 컨텍스트 9개 / wall time ~49s.

```bash
./gradlew test
```

### 평가/벤치마크 테스트 (실 API 키 필요)

실제 OpenAI/Anthropic API 를 호출해 RAG 검색 품질 평가, 청킹 실험, 프롬프트 벤치마크 등을 수행합니다.

```bash
ANTHROPIC_API_KEY=sk-ant-... OPENAI_API_KEY=sk-... ./gradlew evaluationTest

# 또는 태그별
./gradlew experimentTest       # 청킹 전략 실험 (실 OpenAI)
./gradlew qualityEvalTest      # 컨벤션 검색 품질 평가 (실 OpenAI + Anthropic)
./gradlew hybridExperimentTest # 하이브리드 검색 품질 비교 (실 OpenAI)
```

### E2E 시나리오 테스트

실제 Python `ai-agent-service` 컨테이너 + Tempo 컨테이너 + Testcontainers 를 띄워 전체 분산 흐름을 검증합니다. Docker 자원 사용량 큼.

```bash
./gradlew e2eTest
```

---

## 옵저버빌리티

### Langfuse (LLM trace + 토큰/비용)

한 PR 처리의 모든 LLM 호출이 단일 세션 trace 로 집계됩니다.

- **sessionId 전파**: Spring Boot 의 `LangfuseObservationHandler` 가 trace body 에 sessionId 부착 → `AgentAnalysisRequest.sessionId` 로 Python 에 전달 → Python `CallbackHandler.session_id` 로 일관 사용
- **세션 URL**: `http://localhost:3000/project/{projectId}/sessions/{sessionId}`
- **단일 trace cost**: 세션 헤더의 total_cost / total_tokens 가 PR 단위 합산

### Grafana Tempo (분산 trace)

- **W3C `traceparent` 자동 주입**: Spring Boot `RemoteAgentClient` 의 WebClient 가 `ObservationRegistry` 연결로 Micrometer Tracing 이 헤더 자동 주입
- **Python FastAPI 자동 계측**: `FastAPIInstrumentor` 가 수신 trace 를 부모 context 로 채택
- **trace 조회**: Grafana → Explore → Tempo datasource → TraceQL 로 두 서비스 span 의 단일 trace 연결 확인

### Prometheus / Micrometer

- `http://localhost:8080/actuator/prometheus` 에서 메트릭 노출
- 주요 메트릭: `review_cache_*` (hit/miss/savings), `agent_fallback_total{reason}`, `llm_cost_total{model}`, HTTP/JVM 표준

---

## 컨벤션 / 가이드 (개발자용)

- [.claude/guides/version-control.md](.claude/guides/version-control.md) — Conventional Commits (한국어 제목), 브랜치 전략
- [.claude/guides/code-conventions.md](.claude/guides/code-conventions.md) — Kotlin 스타일, 헥사고날 명명, 주석 규칙, DB 인덱스 명명
- [.claude/guides/api-development.md](.claude/guides/api-development.md) — OpenAPI 명세 동기화, 스키마 분리
- [.claude/guides/testing.md](.claude/guides/testing.md) — `@SpringBootTest` 통합 테스트, Mock 금지, 컨텍스트 변형 결정 기준

---

## 라이선스

MIT License
