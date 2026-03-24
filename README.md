# ai-code-reviewer

> Spring Boot 4.0.3 + Kotlin + Spring AI 기반의 AI 코드 리뷰 자동화 시스템

---

## 시스템 개요

`ai-code-reviewer`는 코드 변경사항을 자동으로 분석하고 품질 피드백을 제공하는 AI 코드 리뷰 시스템입니다.

### 핵심 기능

- **자동 코드 리뷰**: Pull Request 또는 코드 스니펫을 AI가 분석하여 개선 사항 제안
- **diff 전처리**: 테스트 파일·잠금 파일 자동 제거, context 줄 수 조정으로 토큰 절감
- **AI 채팅**: 단일 응답 및 SSE 스트리밍 방식으로 자유 형식 AI 대화 지원
- **다양한 AI 백엔드**: Anthropic Claude / OpenAI GPT 프로바이더 선택 지원

### 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Kotlin 2.2.21 |
| 프레임워크 | Spring Boot 4.0.3 |
| AI 통합 | Spring AI 2.0.0-M2 |
| 비동기 | Kotlin Coroutines 1.10.2 |
| 빌드 도구 | Gradle (Kotlin DSL) |
| JDK | JDK 21 |
| DB | PostgreSQL 15 (운영) / H2 (테스트) |
| 기본 모델 | claude-haiku-4-5-20251001 |

---

## 아키텍처

헥사고날 아키텍처(Ports & Adapters)를 따릅니다. 도메인은 외부 시스템을 직접 참조하지 않으며, 포트 인터페이스를 통해 어댑터와 통신합니다.

```
┌─────────────────────────────────────────────────────────────┐
│  Inbound Adapters                                           │
│  ┌────────────────────┐  ┌────────────────────┐             │
│  │  ChatController    │  │  ReviewController  │             │
│  │  POST /api/chat    │  │  POST /api/review  │             │
│  │  POST /api/chat/   │  │                    │             │
│  │       stream (SSE) │  │                    │             │
│  └────────┬───────────┘  └──────────┬─────────┘             │
│           │ ChatUseCase             │ ReviewUseCase          │
│  ┌────────▼───────────┐  ┌──────────▼─────────┐             │
│  │ DefaultChatService │  │DefaultReviewService│  Application│
│  └────────┬───────────┘  └──────────┬─────────┘             │
│           │ AiChatPort              │ AiReviewPort           │
│  ┌────────▼───────────────────────── ▼─────────┐             │
│  │         SpringAiChatAdapter / SpringAiReviewAdapter      │
│  │                  (Outbound Adapters)                     │
│  └─────────────────────────────────────────────┘             │
│                         │                                   │
│                    Spring AI                                │
│              (Anthropic Claude / OpenAI)                    │
└─────────────────────────────────────────────────────────────┘
```

### 계층 역할

| 계층 | 역할 | 주요 클래스 |
|------|------|------------|
| `domain/model` | 순수 도메인 모델 (외부 의존 없음) | `CodeReview`, `CodeIssue`, `DiffFilterOptions` |
| `domain/port/in` | 인바운드 포트 — UseCase 인터페이스 | `ChatUseCase`, `ReviewUseCase` |
| `domain/port/out` | 아웃바운드 포트 — 외부 시스템 추상화 | `AiChatPort`, `AiReviewPort` |
| `domain/service` | 순수 도메인 로직 | `DiffPreprocessor` |
| `application` | UseCase 구현체 — 포트 조합 | `DefaultChatService`, `DefaultReviewService` |
| `adapter/in/web` | HTTP 컨트롤러 | `ChatController`, `ReviewController` |
| `adapter/out/ai` | AI API 클라이언트 | `SpringAiChatAdapter`, `SpringAiReviewAdapter` |

---

## 로컬 실행 방법

### 사전 요구사항

- JDK 21
- Gradle 8.x (또는 `./gradlew` Wrapper 사용)
- LLM API 키 (Anthropic 또는 OpenAI)

### 1. 저장소 클론

```bash
git clone https://github.com/your-org/ai-code-reviewer.git
cd ai-code-reviewer
```

### 2. API 키 설정

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

### 3. 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

### 4. 동작 확인

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health
```

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
  "issues": [
    {
      "title": "SQL Injection 취약점",
      "description": "문자열 연결로 SQL을 구성하면 SQL Injection 공격에 노출됩니다.",
      "category": "SECURITY",
      "severity": "CRITICAL",
      "lineNumber": 1,
      "suggestion": "PreparedStatement 또는 파라미터화된 쿼리를 사용하세요."
    }
  ],
  "summary": "CRITICAL 보안 이슈 1건이 발견되었습니다."
}
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

---

## 프로젝트 구조

```
src/
├── main/
│   ├── kotlin/stillframe42/aicodereviewer/
│   │   ├── AiCodeReviewerApplication.kt
│   │   ├── config/                        # 전역 빈 설정 (@Configuration)
│   │   │   └── ChatClientConfig.kt
│   │   ├── common/                        # 공통 컴포넌트
│   │   │   ├── GlobalExceptionHandler.kt
│   │   │   ├── AiPromptBuilder.kt
│   │   │   └── TokenEstimator.kt
│   │   ├── core/                          # 공통 도메인 타입
│   │   │   └── AiProvider.kt
│   │   ├── chat/                          # 채팅 기능
│   │   │   ├── domain/
│   │   │   │   └── port/in/ChatUseCase.kt
│   │   │   │   └── port/out/AiChatPort.kt
│   │   │   ├── application/DefaultChatService.kt
│   │   │   └── adapter/
│   │   │       ├── in/web/ChatController.kt
│   │   │       └── out/ai/SpringAiChatAdapter.kt
│   │   └── review/                        # 코드 리뷰 기능
│   │       ├── domain/
│   │       │   ├── model/                 # CodeReview, CodeIssue, DiffFilterOptions
│   │       │   ├── service/DiffPreprocessor.kt
│   │       │   └── port/in/ReviewUseCase.kt
│   │       │   └── port/out/AiReviewPort.kt
│   │       ├── application/DefaultReviewService.kt
│   │       └── adapter/
│   │           ├── in/web/ReviewController.kt
│   │           └── out/ai/SpringAiReviewAdapter.kt
│   └── resources/
│       ├── application.yml
│       ├── application-ai.yml             # AI 모델·프롬프트 설정
│       ├── application-db.yml             # DB 설정
│       ├── application-secret.yml         # API 키 (gitignore)
│       └── prompts/
│           ├── review-system-v1.st        # 리뷰 시스템 프롬프트 v1 (기본)
│           ├── review-system-v2.st        # 리뷰 시스템 프롬프트 v2
│           ├── review-system-v3.st        # 리뷰 시스템 프롬프트 v3
│           ├── review-system-v4.st        # 리뷰 시스템 프롬프트 v4
│           ├── review-user.st             # 리뷰 유저 프롬프트
│           ├── chat-system.st             # 채팅 시스템 프롬프트
│           ├── chat-user.st               # 채팅 유저 프롬프트
│           └── README.md                  # 버전별 변경 이력
└── test/
    ├── kotlin/stillframe42/aicodereviewer/
    │   ├── chat/
    │   │   ├── adapter/in/web/ChatControllerTest.kt
    │   │   ├── adapter/in/web/dto/ChatRequestTest.kt
    │   │   └── application/DefaultChatServiceTest.kt
    │   └── review/
    │       ├── adapter/in/web/ReviewControllerTest.kt
    │       ├── application/DefaultReviewServiceTest.kt
    │       ├── domain/service/DiffPreprocessorTest.kt
    │       └── benchmark/                 # 프롬프트 버전별 벤치마크
    │           ├── AbstractVersionBenchmark.kt
    │           ├── PromptV1BenchmarkTest.kt
    │           ├── PromptV2BenchmarkTest.kt
    │           ├── PromptV3BenchmarkTest.kt
    │           ├── PromptV4BenchmarkTest.kt
    │           ├── PromptBenchmarkResult.kt
    │           └── BenchmarkResultStore.kt
    └── resources/
        └── fixtures/review/               # 벤치마크 테스트 픽스처
            ├── security-sql-injection.kt
            ├── security-hardcoded-credentials.kt
            ├── performance-n-plus-one.kt
            ├── performance-inefficient-loop.kt
            ├── readability-magic-numbers.kt
            ├── architecture-spr-violation.kt
            └── clean-simple-function.kt
```

---

## 테스트 실행 방법

### 일반 테스트 (더미 키 환경)

AI API를 호출하지 않는 단위/통합 테스트는 API 키 없이 실행할 수 있습니다.

```bash
./gradlew test
```

### AI 연동 통합 테스트

실제 AI API를 호출하는 테스트는 유효한 API 키가 필요합니다. 키가 없으면 해당 테스트는 자동으로 스킵됩니다.

```bash
ANTHROPIC_API_KEY=sk-ant-... ./gradlew test
```

### 프롬프트 버전별 벤치마크 테스트

v1 / v2 / v3 / v4 시스템 프롬프트 버전 간 이슈 감지 품질을 비교하는 벤치마크입니다.
7개 픽스처 × 4개 버전 = 총 28회 AI 호출이 발생합니다.

```bash
# 더미 키 환경 — AI 호출 없이 구조/컴파일만 확인 (전체 스킵)
./gradlew test --tests "*.benchmark.*"

# 실제 API 키 환경 — 전체 벤치마크 실행
ANTHROPIC_API_KEY=sk-ant-... ./gradlew test --tests "*.benchmark.*"
```

테스트가 완료되면 버전별 비교 결과가 콘솔에 출력됩니다.

```
╔══════════════════════════════════════════════╗
║       프롬프트 버전별 벤치마크 통합 결과          ║
╚══════════════════════════════════════════════╝
시스템 프롬프트 토큰 추정: v1=35 / v2=290 / v3=240 / v4=61

버전 | 픽스처                                 | 점수 | 이슈수 | CRIT | SEC | PERF | READ | ARCH | 응답시간 | 필드완전
--------------------------------------------------------------------
v1  | security-sql-injection                 |  4  |    1  |   0 |   1 |    0 |    0 |    0 |  1200ms | O
v2  | security-sql-injection                 |  3  |    2  |   1 |   2 |    0 |    0 |    0 |  1400ms | O
v3  | security-sql-injection                 |  3  |    3  |   2 |   3 |    0 |    0 |    0 |  1300ms | O
v4  | security-sql-injection                 |  4  |    2  |   1 |   2 |    0 |    0 |    0 |  1100ms | O
...
```

#### 픽스처 목록

| 파일 | 심는 이슈 | 기대 감지 |
|------|---------|---------|
| `security-sql-injection.kt` | 문자열 연결 SQL | SECURITY / CRITICAL |
| `security-hardcoded-credentials.kt` | API 키·비밀번호 하드코딩 | SECURITY / CRITICAL |
| `performance-n-plus-one.kt` | 루프 내 개별 DB 조회 | PERFORMANCE / MAJOR |
| `performance-inefficient-loop.kt` | O(n²) 루프, 문자열 연결 | PERFORMANCE / MAJOR |
| `readability-magic-numbers.kt` | 매직넘버, 불명확한 변수명 | READABILITY |
| `architecture-spr-violation.kt` | SRP 위반 (DB+HTTP+이메일+SMS 단일 클래스) | ARCHITECTURE / MAJOR |
| `clean-simple-function.kt` | 깨끗한 코드 (false positive 측정용) | CRITICAL/MAJOR 0개 기대 |

---

## 라이선스

MIT License
