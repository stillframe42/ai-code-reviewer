# ai-code-reviewer

> Spring Boot 4.0.3 + Kotlin + Spring AI 기반의 AI 코드 리뷰 자동화 시스템

---

## 시스템 개요

`ai-code-reviewer`는 GitHub Pull Request 이벤트를 자동으로 감지해 AI가 코드를 분석하고, 인라인 리뷰 코멘트를 작성하는 코드 리뷰 자동화 시스템입니다.

### 핵심 기능

- **GitHub Webhook 통합**: PR 이벤트(opened, synchronize, reopened) 수신 → AI 리뷰 자동 실행 → 인라인 코멘트 작성
- **자동 코드 리뷰**: Pull Request diff 또는 코드 스니펫을 AI가 분석하여 개선 사항 제안
- **diff 전처리**: 테스트 파일·잠금 파일 자동 제거, context 줄 수 조정으로 토큰 절감
- **Tool Calling 리뷰**: GitHub API를 도구로 활용해 파일별 상세 정보를 조회하는 고급 리뷰 모드
- **AI 채팅**: 단일 응답 및 SSE 스트리밍 방식으로 자유 형식 AI 대화 지원
- **다양한 AI 백엔드**: Anthropic Claude / OpenAI GPT 프로바이더 선택 지원
- **리뷰 이력 관리**: 리뷰 결과 PostgreSQL 저장, PR별·통계 조회 API 제공

### 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Kotlin 2.2.21 |
| 프레임워크 | Spring Boot 4.0.3 |
| AI 통합 | Spring AI 2.0.0-M2 |
| 비동기 | Kotlin Coroutines 1.10.2 |
| 빌드 도구 | Gradle (Kotlin DSL) |
| JDK | JDK 21 |
| DB | PostgreSQL (운영·테스트, Testcontainers) |
| DB 마이그레이션 | Flyway 10+ |
| 캐시 | Redis |
| 옵저버빌리티 | Langfuse, Micrometer |
| 기본 모델 | claude-haiku-4-5-20251001 |

---

## 아키텍처

자세한 아키텍처 다이어그램은 [docs/architecture.md](docs/architecture.md)를 참조하세요.

---

## 로컬 실행 방법

### 사전 요구사항

- JDK 21
- Gradle 8.x (또는 `./gradlew` Wrapper 사용)
- Docker (PostgreSQL 컨테이너 실행용)
- LLM API 키 (Anthropic 또는 OpenAI)

### 1. 저장소 클론

```bash
git clone https://github.com/stillframe42/ai-code-reviewer.git
cd ai-code-reviewer
```

### 2. PostgreSQL 실행

```bash
docker-compose up -d
```

기본 설정: `localhost:15432`, DB `aireviewer`, 사용자 `aireviewer`/`aireviewer`

### 3. API 키 설정

`src/main/resources/application-secret.yml` 파일을 생성하고 API 키를 설정합니다.
(이 파일은 `.gitignore`에 등록되어 있어 커밋되지 않습니다.)

```yaml
anthropic:
  api-key: sk-ant-...

openai:
  api-key: sk-...
```

또는 환경 변수로 직접 전달할 수 있습니다.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
```

### 4. 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

### 5. 동작 확인

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health
```

---

## GitHub App 설정 (Webhook 자동화)

GitHub PR 이벤트를 수신하고 자동 리뷰를 실행하려면 GitHub App 등록이 필요합니다.

### application-github.yml 설정

`src/main/resources/application-secret.yml`에 아래 항목을 추가합니다.

```yaml
app:
  github:
    webhook-secret: <GitHub App Webhook Secret>
    app-id: <GitHub App ID>
    private-key-path: <RSA 개인키 파일 경로 (.pem)>
```

### Webhook 이벤트 처리 흐름

1. GitHub PR 이벤트 수신 (`POST /api/github/webhook`)
2. HMAC-SHA256 서명 검증
3. 중복 이벤트 확인 (repository + PR 번호 + head SHA 기준)
4. PR diff 조회 및 전처리
5. AI 코드 리뷰 실행
6. 리뷰 결과 DB 저장
7. GitHub PR에 인라인 코멘트 작성

---

## API 사용 예시

### POST /api/chat — 단일 응답

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "코틀린에서 data class와 일반 class의 차이점을 설명해줘",
    "provider": "ANTHROPIC"
  }'
```

```json
{
  "answer": "data class는 equals(), hashCode(), copy(), toString()을 자동 생성합니다..."
}
```

### POST /api/chat/stream — SSE 스트리밍

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "message": "Spring AI란 무엇인가요?",
    "provider": "ANTHROPIC"
  }'
```

```
data: Spring

data: AI는

data: LLM을 ...
```

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

#### `strategyOverrides` 사용 예시

`FileExtensionClassifier`의 자동 분류를 덮어쓸 때 사용합니다.

```bash
curl -X POST http://localhost:8080/api/review \
  -H "Content-Type: application/json" \
  -d '{
    "code": "<git diff 문자열>",
    "diffOptions": {
      "strategyOverrides": {
        "docs/**": "Skip",
        "infra/**": "QueryReview",
        "src/main/kotlin/**": "FullReview"
      }
    }
  }'
```

#### `FileReviewStrategy` 값

| 값 | 설명 | 기본 적용 확장자 |
|----|------|----------------|
| `FullReview` | 코드 품질 전체 리뷰 | `.kt`, `.java`, `.ts`, `.py` 등 |
| `QueryReview` | 변경 의도·설정값 위주 리뷰 | `.yml`, `.sql`, `.json`, `Dockerfile` 등 |
| `Skip` | 리뷰 대상에서 완전히 제외 | `.png`, `.jar`, `gradlew` 등 |

#### 이슈 카테고리

| 값 | 설명 |
|----|------|
| `PERFORMANCE` | 성능 문제 (N+1 쿼리, 비효율 루프 등) |
| `SECURITY` | 보안 취약점 (SQL Injection, 하드코딩 자격증명 등) |
| `READABILITY` | 가독성 문제 (매직넘버, 불명확한 변수명 등) |
| `ARCHITECTURE` | 설계 문제 (SRP 위반, 의존성 방향 오류 등) |

#### 이슈 심각도

| 값 | 설명 |
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
  "categoryDistribution": {
    "SECURITY": 15,
    "PERFORMANCE": 32,
    "READABILITY": 48,
    "ARCHITECTURE": 33
  },
  "averageToolCallCount": 2.4,
  "costByModel": {
    "claude-haiku-4-5-20251001": 0.0142
  },
  "cacheHitRate": 0.35,
  "estimatedSavings": 0.0051
}
```

---

## 데이터베이스 스키마

Flyway로 마이그레이션을 관리합니다. `src/main/resources/db/migration/` 에 버전별 SQL이 있습니다.

| 테이블 | 설명 |
|--------|------|
| `processed_pull_request_event` | 처리된 Webhook 이벤트 기록 (중복 처리 방지) |
| `review_requests` | 리뷰 요청 상태 추적 (PENDING → PROCESSING → DONE / FAILED) |
| `review_results` | AI 리뷰 결과 (요약, 이슈 목록 JSON, 모델명) |
| `tool_call_logs` | Tool Calling 호출 이력 (도구명, 인자, 응답 크기, 소요 시간) |
| `review_issue_categories` | 이슈 카테고리 집계용 정규화 테이블 |
| `llm_cost_logs` | LLM 호출별 비용 기록 (모델명, 프롬프트·완성 토큰, 추정 비용) |

---

## 프롬프트 버전

현재 활성 버전: **v8** (`application-ai.yml`에서 변경 가능)

| 버전 | 주요 변경 |
|------|---------|
| v1 | 초기: 단순 역할 정의 |
| v2 | few-shot 예시, 카테고리·심각도 정의 추가 |
| v3 | 토큰 최적화 (메타 설명 제거) |
| v4 | v1 기반 재정립 (필수 스키마만 유지) |
| v5 | `[QUERY_REVIEW]` 헤더 파일 처리 지침 추가 |
| v6 | `filename` 필드 안내 (diff position 매핑용) |
| v7 | summary 2문장 제한, positives 최대 3개 제한 |
| **v8** | **현재**: Tool Calling 사용 지침 추가 |

버전별 상세 변경 이력은 `src/main/resources/prompts/README.md` 참조.

---

## 프로젝트 구조

```
src/
├── main/
│   ├── kotlin/stillframe42/aicodereviewer/
│   │   ├── AiCodeReviewerApplication.kt
│   │   ├── core/
│   │   │   └── AiProvider.kt                  # ANTHROPIC, OPENAI 열거형
│   │   ├── config/                             # 전역 빈 설정 (@Configuration, @ConfigurationProperties)
│   │   │   ├── AdvisorConfig.kt               # Spring AI Advisor 빈 조립
│   │   │   ├── ChatClientConfig.kt
│   │   │   ├── ReviewConfig.kt
│   │   │   ├── GitHubConfig.kt
│   │   │   ├── JacksonConfig.kt
│   │   │   ├── RedisConfig.kt
│   │   │   ├── LangfuseObservationConfig.kt
│   │   │   ├── ToolObservationConfig.kt
│   │   │   ├── AiReviewerProperties.kt        # @ConfigurationProperties
│   │   │   ├── GitHubProperties.kt
│   │   │   ├── LangfuseProperties.kt
│   │   │   ├── LlmCostProperties.kt
│   │   │   └── ReviewProperties.kt
│   │   ├── common/                             # 기능 횡단 공통 컴포넌트
│   │   │   ├── GlobalExceptionHandler.kt
│   │   │   ├── AiPromptBuilder.kt
│   │   │   ├── TokenEstimator.kt
│   │   │   ├── Logging.kt                     # Logger 위임 인터페이스
│   │   │   ├── advisor/                       # Spring AI Advisor 구현체
│   │   │   │   ├── CostTrackingAdvisor.kt
│   │   │   │   ├── LoggingAdvisor.kt
│   │   │   │   └── RetryAdvisor.kt
│   │   │   ├── cache/
│   │   │   │   └── AbstractRedisCacheAdapter.kt
│   │   │   ├── langfuse/                      # Langfuse 옵저버빌리티
│   │   │   │   ├── LangfuseClient.kt
│   │   │   │   ├── LangfuseObservationHandler.kt
│   │   │   │   └── ...
│   │   │   ├── metrics/                       # Micrometer 메트릭
│   │   │   │   ├── LlmMetrics.kt
│   │   │   │   ├── ReviewMetrics.kt
│   │   │   │   └── ...
│   │   │   └── port/
│   │   │       └── CostLogPort.kt             # Advisor → persistence 역방향 의존 제거용 포트
│   │   ├── chat/                               # 채팅 기능
│   │   │   ├── domain/port/in/ChatUseCase.kt
│   │   │   ├── domain/port/out/AiChatPort.kt
│   │   │   ├── application/DefaultChatService.kt
│   │   │   └── adapter/
│   │   │       ├── in/web/ChatController.kt
│   │   │       └── out/ai/SpringAiChatAdapter.kt
│   │   ├── review/                             # 코드 리뷰 기능
│   │   │   ├── domain/
│   │   │   │   ├── model/                      # CodeReview, CodeIssue, DiffFilterOptions 등
│   │   │   │   ├── service/
│   │   │   │   │   ├── DiffPreprocessor.kt
│   │   │   │   │   ├── FileExtensionClassifier.kt
│   │   │   │   │   ├── AiModelSelector.kt     # PrImportance → 모델명 선택 도메인 서비스
│   │   │   │   │   └── PrImportanceAnalyzer.kt
│   │   │   │   └── port/
│   │   │   │       ├── in/ReviewUseCase.kt
│   │   │   │       ├── in/ReviewQueryUseCase.kt
│   │   │   │       ├── in/ReviewQueryResult.kt # UseCase 반환 결과 타입 (port/in에 위치)
│   │   │   │       ├── out/AiReviewPort.kt
│   │   │   │       ├── out/ReviewPersistencePort.kt
│   │   │   │       ├── out/ReviewQueryPort.kt
│   │   │   │       ├── out/ReviewCacheStore.kt
│   │   │   │       └── out/ReviewCacheStatsStore.kt
│   │   │   ├── application/
│   │   │   │   ├── DefaultReviewService.kt
│   │   │   │   └── DefaultReviewQueryService.kt
│   │   │   └── adapter/
│   │   │       ├── in/web/ReviewController.kt
│   │   │       ├── in/web/ReviewQueryController.kt
│   │   │       ├── out/ai/SpringAiReviewAdapter.kt
│   │   │       ├── out/cache/                 # Redis 캐시 어댑터
│   │   │       │   ├── RedisReviewCacheAdapter.kt
│   │   │       │   └── RedisReviewCacheStatsAdapter.kt
│   │   │       └── out/persistence/
│   │   │           ├── ReviewPersistenceAdapter.kt
│   │   │           ├── ReviewQueryAdapter.kt
│   │   │           └── CostLogAdapter.kt      # CostLogPort 구현체
│   │   └── github/                             # GitHub Webhook & API 통합
│   │       ├── domain/
│   │       │   ├── model/                      # PullRequestEvent, PrFile 등
│   │       │   ├── service/DiffPositionResolver.kt
│   │       │   └── port/
│   │       │       ├── in/GitHubWebhookUseCase.kt
│   │       │       └── out/GitHubApiPort.kt, GitHubTokenPort.kt 등
│   │       ├── application/DefaultGitHubWebhookService.kt
│   │       └── adapter/
│   │           ├── in/web/WebhookController.kt
│   │           ├── in/web/HmacSignatureVerifier.kt
│   │           ├── out/github/
│   │           │   ├── GitHubApiAdapter.kt
│   │           │   ├── GitHubAppTokenProvider.kt
│   │           │   ├── GitHubAppJwtGenerator.kt
│   │           │   ├── JwtSigner.kt           # RS256 JWT 서명 (GitHub App 전용)
│   │           │   ├── RsaKeyLoader.kt        # PEM → PrivateKey 변환 (GitHub App 전용)
│   │           │   ├── client/GitHubHttpClient.kt
│   │           │   └── ratelimit/             # GitHub API Rate Limit 관리
│   │           ├── out/formatter/MarkdownReviewCommentFormatter.kt
│   │           └── out/persistence/ProcessedEventAdapter.kt
│   └── resources/
│       ├── application.yml
│       ├── application-ai.yml                  # AI 모델·프롬프트 설정
│       ├── application-db.yml                  # DB 설정
│       ├── application-github.yml              # GitHub App 설정
│       ├── application-secret.yml              # API 키 (gitignore)
│       ├── db/migration/                       # Flyway 마이그레이션
│       │   ├── V1__create_processed_event_table.sql
│       │   ├── V2__create_review_tables.sql
│       │   ├── V3__add_tool_call_count_and_issue_categories.sql
│       │   └── V4__create_llm_cost_logs.sql
│       └── prompts/
│           ├── review-system-v1.st ~ v8.st     # 리뷰 시스템 프롬프트 버전별
│           ├── review-user.st
│           ├── chat-system.st
│           ├── chat-user.st
│           └── README.md                       # 버전별 변경 이력
└── test/
    ├── kotlin/stillframe42/aicodereviewer/
    │   ├── chat/                               # 채팅 단위/통합 테스트
    │   ├── review/                             # 리뷰 단위/통합 테스트
    │   │   └── benchmark/                      # 프롬프트 버전별 벤치마크
    │   └── github/                             # GitHub Webhook 통합 테스트
    │       └── WebhookFlowIntegrationTest.kt   # WireMock + Testcontainers
    └── resources/
        ├── fixtures/review/                    # 벤치마크 테스트용 코드 픽스처
        └── test-keys/                          # JWT 테스트용 RSA 키
```

---

## 테스트 실행 방법

### 일반 테스트

AI API 호출이 없는 단위/통합 테스트는 API 키 없이 실행할 수 있습니다.
통합 테스트는 Testcontainers로 PostgreSQL을 자동 실행하며, 외부 API는 WireMock으로 모킹합니다.

```bash
./gradlew test
```

### AI 연동 통합 테스트

실제 AI API를 호출하는 테스트는 유효한 API 키가 필요합니다. 키가 없으면 해당 테스트는 자동으로 스킵됩니다.

```bash
ANTHROPIC_API_KEY=sk-ant-... ./gradlew test
```

## 라이선스

MIT License
