# 아키텍처

> GitHub PR Webhook 수신부터 AI 코드 리뷰 코멘트 등록까지의 전체 시스템 구조를 설명합니다.

---

## 1. 시스템 전체 흐름

GitHub PR 이벤트를 수신하고 AI 리뷰 결과를 PR에 등록하는 전체 시퀀스입니다.
Webhook은 202를 즉시 반환하여 GitHub 타임아웃을 방지하고, 실제 리뷰는 비동기로 처리됩니다.
Redis 캐시를 통해 동일 커밋의 반복 리뷰를 방지합니다.

```mermaid
sequenceDiagram
    participant GH as GitHub
    participant SB as Spring Boot
    participant DB as PostgreSQL
    participant Redis
    participant GHAPI as GitHub API
    participant LLM

    GH->>SB: PR Webhook (POST /api/github/webhook)
    SB-->>GH: 202 Accepted (즉시 반환)
    SB->>DB: 중복 이벤트 확인 (processed_pull_request_event)
    SB->>GHAPI: PR diff 수집
    SB->>SB: diff 전처리 (테스트·잠금 파일 제거, context 조정)
    SB->>Redis: 캐시 조회 (repoFullName + prNumber + headSha)
    alt 캐시 히트
        Redis-->>SB: 캐시된 리뷰 결과
    else 캐시 미스
        SB->>LLM: 리뷰 요청 (Tool Calling 포함)
        loop Tool Calling
            LLM->>SB: Tool 호출 요청 (파일 조회 등)
            SB->>GHAPI: 추가 파일 조회
            GHAPI-->>SB: 파일 내용
            SB-->>LLM: Tool 결과 전달
        end
        LLM-->>SB: 최종 리뷰 결과
        SB->>Redis: 결과 캐시 저장
    end
    SB->>DB: 리뷰 결과 저장
    SB->>GHAPI: PR 인라인 코멘트 등록
```

---

## 2. 헥사고날 아키텍처

의존성 방향: **Inbound Adapters → Application → Domain ← Outbound Adapters**.
Domain은 외부를 직접 참조하지 않으며, Port 인터페이스를 통해 Outbound Adapter와 통신합니다.

```mermaid
flowchart TB
    subgraph Inbound["Inbound Adapters (adapter/in/web)"]
        WebhookCtrl["WebhookController\nPOST /api/github/webhook"]
        ReviewCtrl["ReviewController\nPOST /api/review"]
        ReviewQueryCtrl["ReviewQueryController\nGET /api/reviews/..."]
        ChatCtrl["ChatController\nPOST /api/chat"]
    end

    subgraph Application["Application Layer (application)"]
        WebhookSvc["DefaultGitHubWebhookService"]
        ReviewSvc["DefaultReviewService"]
        QuerySvc["DefaultReviewQueryService"]
        ChatSvc["DefaultChatService"]
    end

    subgraph Domain["Domain Layer"]
        subgraph PortIn["Port / In (UseCase)"]
            GitHubWebhookUC["GitHubWebhookUseCase"]
            ReviewUC["ReviewUseCase"]
            ReviewQueryUC["ReviewQueryUseCase"]
            ChatUC["ChatUseCase"]
        end
        subgraph DomainSvc["Domain Service"]
            DiffPre["DiffPreprocessor"]
            ModelSel["AiModelSelector"]
            PrAnalyzer["PrImportanceAnalyzer"]
        end
        subgraph PortOut["Port / Out"]
            AiReviewPort["AiReviewPort"]
            PersistPort["ReviewPersistencePort"]
            CacheStore["ReviewCacheStore"]
            GitHubApiPort["GitHubApiPort"]
            CostLogPort["CostLogPort"]
        end
    end

    subgraph Outbound["Outbound Adapters (adapter/out)"]
        SpringAiAdapter["SpringAiReviewAdapter"]
        PersistAdapter["ReviewPersistenceAdapter"]
        CacheAdapter["RedisReviewCacheAdapter"]
        GitHubAdapter["GitHubApiAdapter"]
        CostAdapter["CostLogAdapter"]
    end

    subgraph External["External Systems"]
        LLM["LLM\n(Anthropic / OpenAI)"]
        PG["PostgreSQL"]
        Redis["Redis"]
        GHAPI["GitHub API"]
    end

    Inbound --> Application
    Application --> Domain
    PortOut --> Outbound
    SpringAiAdapter --> LLM
    PersistAdapter --> PG
    CacheAdapter --> Redis
    GitHubAdapter --> GHAPI
    CostAdapter --> PG
```

### 계층 역할

| 계층 | 역할 | 주요 클래스 |
|------|------|------------|
| `domain/model` | 순수 도메인 모델 (외부 의존 없음) | `CodeReview`, `CodeIssue`, `DiffFilterOptions`, `PullRequestEvent` |
| `domain/port/in` | 인바운드 포트 — UseCase 인터페이스·결과 타입 | `ChatUseCase`, `ReviewUseCase`, `ReviewQueryUseCase`, `ReviewQueryResult`, `GitHubWebhookUseCase` |
| `domain/port/out` | 아웃바운드 포트 — 외부 시스템 추상화 | `AiChatPort`, `AiReviewPort`, `ReviewPersistencePort`, `ReviewCacheStore`, `GitHubApiPort`, `ProcessedEventPort` |
| `domain/service` | 순수 도메인 로직 | `DiffPreprocessor`, `FileExtensionClassifier`, `AiModelSelector`, `PrImportanceAnalyzer`, `DiffPositionResolver` |
| `application` | UseCase 구현체 — 포트 조합 | `DefaultChatService`, `DefaultReviewService`, `DefaultReviewQueryService`, `DefaultGitHubWebhookService` |
| `adapter/in/web` | HTTP 컨트롤러 | `ChatController`, `ReviewController`, `ReviewQueryController`, `WebhookController` |
| `adapter/out/ai` | AI API 클라이언트 | `SpringAiChatAdapter`, `SpringAiReviewAdapter` |
| `adapter/out/github` | GitHub API 클라이언트 | `GitHubApiAdapter`, `GitHubAppTokenProvider`, `JwtSigner`, `RsaKeyLoader` |
| `adapter/out/persistence` | DB 영속성 어댑터 | `ReviewPersistenceAdapter`, `ReviewQueryAdapter`, `CostLogAdapter`, `ProcessedEventAdapter` |
| `adapter/out/cache` | Redis 캐시 어댑터 | `RedisReviewCacheAdapter`, `RedisReviewCacheStatsAdapter` |
| `adapter/out/formatter` | 포맷팅 어댑터 | `MarkdownReviewCommentFormatter` |
| `common/advisor` | Spring AI Advisor (횡단 관심사) | `CostTrackingAdvisor`, `LoggingAdvisor`, `RetryAdvisor` |
| `common/port` | 공통 아웃바운드 포트 | `CostLogPort` |
| `common/metrics` | Micrometer 메트릭 | `LlmMetrics`, `ReviewMetrics` |
| `common/langfuse` | Langfuse 옵저버빌리티 | `LangfuseClient`, `LangfuseObservationHandler` |

---

## 3. Advisor 체인

Spring AI의 Advisor는 LLM 호출을 감싸는 데코레이터 패턴으로 동작합니다.
요청은 Advisor 순서대로 통과하고, 응답은 역순으로 반환됩니다.
`CostTrackingAdvisor`는 응답 수신 시 토큰·비용을 집계하여 DB에 기록하는 사이드 이펙트를 가집니다.

```mermaid
flowchart LR
    Client["ChatClient"]
    LogA["LoggingAdvisor\n요청·응답 로깅"]
    RetryA["RetryAdvisor\n지수 백오프 재시도"]
    CostA["CostTrackingAdvisor\n토큰·비용 집계"]
    LLM["LLM API"]
    CostPort["CostLogPort"]
    DB["PostgreSQL\nllm_cost_logs"]

    Client -->|"요청"| LogA
    LogA -->|"요청"| RetryA
    RetryA -->|"요청"| CostA
    CostA -->|"요청"| LLM
    LLM -->|"응답"| CostA
    CostA -->|"응답"| RetryA
    RetryA -->|"응답"| LogA
    LogA -->|"응답"| Client
    CostA -.->|"비용 기록"| CostPort
    CostPort -.->|"저장"| DB
```

---

## 4. Redis 캐시 흐름

`DefaultReviewService`는 LLM 호출 전에 Redis 캐시를 먼저 조회합니다.
캐시 키는 `repoFullName + prNumber + headSha` 조합으로, 동일 커밋의 반복 리뷰를 방지합니다.
캐시 히트율은 `RedisReviewCacheStatsAdapter`가 집계하며 `/api/reviews/stats` 응답에 포함됩니다.

```mermaid
sequenceDiagram
    participant Svc as DefaultReviewService
    participant Cache as RedisReviewCacheAdapter
    participant Stats as RedisReviewCacheStatsAdapter
    participant LLM as SpringAiReviewAdapter

    Svc->>Cache: get(repoFullName + prNumber + headSha)
    alt 캐시 히트
        Cache-->>Svc: CodeReview (cached)
        Svc->>Stats: recordHit()
    else 캐시 미스
        Cache-->>Svc: null
        Svc->>LLM: review(context)
        LLM-->>Svc: CodeReview
        Svc->>Cache: put(key, CodeReview)
        Svc->>Stats: recordMiss()
    end
```

---

## 5. PR 중요도 라우팅

`PrImportanceAnalyzer`가 변경 파일 수·확장자를 분석하여 PR을 CRITICAL / NORMAL로 분류합니다.
`AiModelSelector`는 분류 결과에 따라 `AiReviewerProperties`에서 모델명을 선택합니다.
CRITICAL PR에는 더 강력한 모델(예: claude-opus-4-6)이, NORMAL PR에는 기본 모델(예: claude-haiku-4-5-20251001)이 사용됩니다.

```mermaid
flowchart TD
    Webhook["GitHub PR Webhook"]
    Analyzer["PrImportanceAnalyzer\n변경 파일 수·확장자 분석"]
    Critical["AiModelSelector\n→ criticalModel\n예: claude-opus-4-6"]
    Normal["AiModelSelector\n→ defaultModel\n예: claude-haiku-4-5-20251001"]
    ReviewSvc["DefaultReviewService\n선택된 모델로 AI 리뷰 실행"]

    Webhook --> Analyzer
    Analyzer -->|"CRITICAL"| Critical
    Analyzer -->|"NORMAL"| Normal
    Critical --> ReviewSvc
    Normal --> ReviewSvc
```
