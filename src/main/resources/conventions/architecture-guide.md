# 아키텍처 가이드

이 문서는 프로젝트의 소프트웨어 아키텍처 원칙과 설계 규칙을 정의한다.
헥사고날 아키텍처(Ports & Adapters 패턴)를 기반으로 하며,
코드 리뷰 시 이 원칙에 위배되는 구조는 반드시 지적한다.

---

## 1. 헥사고날 아키텍처 개요

### 1.1 핵심 원칙

헥사고날 아키텍처(Hexagonal Architecture, Ports & Adapters)는 애플리케이션의 핵심 비즈니스 로직을 외부 시스템(DB, API, UI)으로부터 완전히 분리하는 설계 패턴이다.

**핵심 목표:**
- 도메인 로직이 인프라스트럭처에 의존하지 않는다
- 외부 시스템을 쉽게 교체할 수 있다 (DB 교체, API 교체)
- 도메인 로직을 독립적으로 테스트할 수 있다

### 1.2 계층 구조

```
┌─────────────────────────────────────────┐
│              Adapter Layer              │
│  ┌────────────┐    ┌────────────────┐   │
│  │  in/web    │    │   out/ai       │   │
│  │ Controller │    │ SpringAiAdapter│   │
│  └─────┬──────┘    └───────┬────────┘   │
│        │                   │            │
│  ┌─────▼──────────────────▼─────────┐  │
│  │         Application Layer        │  │
│  │      DefaultReviewService        │  │
│  └─────┬──────────────────┬─────────┘  │
│        │                  │            │
│  ┌─────▼──────────────────▼─────────┐  │
│  │          Domain Layer            │  │
│  │  model / port/in / port/out      │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

---

## 2. 계층별 역할과 책임

### 2.1 Domain Layer

**역할:** 순수 비즈니스 로직. 외부 의존성 없음.

| 패키지 | 내용 | 예시 |
|--------|------|------|
| `domain/model` | 도메인 모델 (data class) | `CodeReview`, `CodeIssue`, `PrFile` |
| `domain/port/in` | 인바운드 포트 (UseCase 인터페이스) | `ReviewUseCase`, `ChatUseCase` |
| `domain/port/out` | 아웃바운드 포트 (외부 시스템 인터페이스) | `AiReviewPort`, `GitHubApiPort` |
| `domain/service` | 도메인 서비스 (순수 로직) | `DiffPreprocessor`, `FileExtensionClassifier` |

```kotlin
// ✅ 도메인 모델 — 외부 의존성 없는 순수 Kotlin
data class CodeIssue(
    val file: String,
    val line: Int,
    val category: IssueCategory,
    val severity: IssueSeverity,
    val message: String,
    val suggestion: String? = null,
)

// ✅ 인바운드 포트 — UseCase 인터페이스
interface ReviewUseCase {
    suspend fun requestReview(request: ReviewRequest): ReviewResponse
}

// ✅ 아웃바운드 포트 — 외부 AI 시스템 추상화
interface AiReviewPort {
    suspend fun reviewCode(context: ReviewContext): CodeReview
}
```

**금지 사항:**
```kotlin
// ❌ 도메인 모델에 Spring 어노테이션 금지
data class CodeIssue(
    @field:JsonProperty("file_path")  // ← 도메인 모델에 Jackson 어노테이션 금지
    val file: String,
)

// ❌ 도메인 포트에 구현체 의존 금지
interface ReviewUseCase {
    fun getSpringAiClient(): ChatClient  // ← 인프라 의존 금지
}
```

### 2.2 Application Layer

**역할:** UseCase 인터페이스 구현체. 도메인 포트를 조합하여 비즈니스 흐름을 조율한다.

```kotlin
// ✅ Application Layer — 포트를 조합하는 UseCase 구현체
@Service
class DefaultReviewService(
    private val aiReviewPort: AiReviewPort,           // 아웃바운드 포트 주입
    private val gitHubApiPort: GitHubApiPort,         // 아웃바운드 포트 주입
    private val reviewPersistencePort: ReviewPersistencePort,
) : ReviewUseCase {

    override suspend fun requestReview(request: ReviewRequest): ReviewResponse {
        val prFiles = gitHubApiPort.fetchPrFiles(request.prNumber)
        val review = aiReviewPort.reviewCode(ReviewContext(prFiles))
        reviewPersistencePort.save(review)
        return ReviewResponse.from(review)
    }
}
```

**금지 사항:**
```kotlin
// ❌ Application Layer에서 인프라 직접 접근 금지
@Service
class DefaultReviewService(
    private val chatClient: ChatClient,  // ← Spring AI 직접 의존 금지
    private val jdbcTemplate: JdbcTemplate,  // ← DB 직접 접근 금지
)
```

### 2.3 Adapter Layer

**인바운드 어댑터 (`adapter/in/web`):**
```kotlin
// ✅ Controller는 UseCase 인터페이스에만 의존
@RestController
@RequestMapping("/api")
class ReviewController(
    private val reviewUseCase: ReviewUseCase,  // ← 인터페이스 의존
) {
    @PostMapping("/review")
    suspend fun requestReview(@RequestBody request: ReviewRequest): ReviewResponse =
        reviewUseCase.requestReview(request)
}

// ❌ Controller에서 Service 구현체 직접 의존 금지
class ReviewController(
    private val defaultReviewService: DefaultReviewService,  // ← 구현체 직접 의존 금지
)
```

**아웃바운드 어댑터 (`adapter/out/ai`, `adapter/out/persistence`):**
```kotlin
// ✅ 아웃바운드 어댑터는 포트 인터페이스를 구현
@Component
class SpringAiReviewAdapter(
    private val chatClient: ChatClient,
) : AiReviewPort {  // ← 포트 구현

    override suspend fun reviewCode(context: ReviewContext): CodeReview {
        // Spring AI 구체 구현
    }
}
```

---

## 3. 의존성 방향 규칙

### 3.1 의존성 방향: 바깥 → 안쪽

```
Adapter → Application → Domain
```

- Domain은 Application이나 Adapter에 의존하지 않는다
- Application은 Adapter에 의존하지 않는다
- Adapter만 Application과 Domain에 의존할 수 있다

```kotlin
// ✅ 올바른 의존성 방향
// Adapter → Application Port (UseCase)
class ReviewController(private val reviewUseCase: ReviewUseCase)

// Application → Domain Port (out)
class DefaultReviewService(private val aiReviewPort: AiReviewPort)

// ❌ 잘못된 의존성 방향
// Domain → Adapter (역방향 의존 — 절대 금지)
class CodeReview(private val springAiReviewAdapter: SpringAiReviewAdapter)

// Application → Adapter 구현체 (역방향 의존 — 절대 금지)
class DefaultReviewService(private val springAiReviewAdapter: SpringAiReviewAdapter)
```

---

## 4. 패키지 구조 규칙

### 4.1 기능(Feature) 단위 패키지 구성

```
stillframe42.aicodereviewer/
├── config/          ← 전역 @Configuration 빈
├── common/          ← 공통 컴포넌트 (예외 핸들러, 유틸리티)
├── core/            ← 공통 도메인 타입 (열거형 등)
├── chat/            ← 채팅 기능
│   ├── domain/
│   │   ├── model/
│   │   ├── port/in/
│   │   └── port/out/
│   ├── application/
│   └── adapter/
│       ├── in/web/
│       └── out/ai/
└── review/          ← 코드 리뷰 기능
    ├── domain/
    │   ├── model/
    │   ├── service/
    │   └── port/
    │       ├── in/
    │       └── out/
    ├── application/
    └── adapter/
        ├── in/web/
        └── out/
            ├── ai/
            └── persistence/
```

### 4.2 잘못된 패키지 구조 예시

```kotlin
// ❌ 기술 계층별 패키지 분리 — 금지
package stillframe42.aicodereviewer.controller  // 모든 컨트롤러를 하나로
package stillframe42.aicodereviewer.service     // 모든 서비스를 하나로
package stillframe42.aicodereviewer.repository  // 모든 레포지토리를 하나로

// ✅ 기능별 패키지 분리 — 권장
package stillframe42.aicodereviewer.review.adapter.in.web
package stillframe42.aicodereviewer.review.application
package stillframe42.aicodereviewer.review.adapter.out.persistence
```

---

## 5. 명명 규칙

### 5.1 계층별 클래스 명명

| 계층 | 패턴 | 예시 |
|------|------|------|
| 인바운드 포트 (UseCase) | `{기능}UseCase` | `ReviewUseCase`, `ChatUseCase` |
| Application 구현체 | `Default{기능}Service` | `DefaultReviewService` |
| 아웃바운드 포트 | `{기능}Port` 또는 `Ai{기능}Port` | `AiReviewPort`, `GitHubApiPort` |
| 인바운드 어댑터 | `{기능}Controller` | `ReviewController` |
| 아웃바운드 어댑터 (AI) | `SpringAi{기능}Adapter` | `SpringAiReviewAdapter` |
| 아웃바운드 어댑터 (DB) | `{기능}Adapter` | `ProcessedEventAdapter` |
| JPA Entity | `{기능}Entity` | `ReviewRequestEntity` |
| DTO (요청) | `{기능}Request` | `ReviewRequest` |
| DTO (응답) | `{기능}Response` | `ReviewResponse` |

---

## 6. 트랜잭션 경계 설계

### 6.1 @Transactional 위치

`@Transactional`은 Application Layer(UseCase 구현체)에서만 선언한다.

```kotlin
// ✅ Application Layer에서 트랜잭션 선언
@Service
class DefaultReviewService(...) : ReviewUseCase {

    @Transactional
    override suspend fun requestReview(request: ReviewRequest): ReviewResponse {
        // 트랜잭션 경계 내에서 DB 작업
    }
}

// ❌ Repository/Adapter에서 트랜잭션 선언 — 지양
@Repository
class ReviewRequestRepository : JpaRepository<ReviewRequestEntity, Long> {
    @Transactional  // ← Application 레이어에서 관리해야 함
    fun saveAndProcess(entity: ReviewRequestEntity): ReviewRequestEntity
}
```

### 6.2 읽기 전용 트랜잭션 분리

조회 전용 메서드는 `@Transactional(readOnly = true)`를 명시한다.

```kotlin
// ✅ 읽기 전용 트랜잭션 분리
@Service
class DefaultReviewQueryService(...) : ReviewQueryUseCase {

    @Transactional(readOnly = true)
    override suspend fun findReviewById(id: Long): ReviewResponse {
        return reviewPersistencePort.findById(id)
            ?: throw ReviewNotFoundException(id)
    }

    @Transactional(readOnly = true)
    override suspend fun findAllByRepo(repoFullName: String): List<ReviewSummary> {
        return reviewPersistencePort.findAllByRepo(repoFullName)
    }
}
```

**readOnly = true의 효과:**
- Hibernate의 dirty checking 비활성화 → 성능 향상
- 데이터 변경 시도 시 예외 발생 → 안전성 향상
- DB 읽기 전용 커넥션 사용 가능 (읽기 복제본 연결 시)

---

## 7. DB 인덱스 명명 규칙

| 종류 | 접두사 | 예시 |
|------|--------|------|
| Primary Key | `pk_` | `pk_review_requests` |
| Unique Index | `ux_` | `ux_processed_event` |
| 일반 Index | `ix_` | `ix_review_requests_repo_pr` |

```sql
-- ✅ 올바른 인덱스 명명
CREATE TABLE review_requests (
    id BIGSERIAL CONSTRAINT pk_review_requests PRIMARY KEY,
    ...
);
CREATE UNIQUE INDEX ux_processed_event
    ON processed_pull_request_event (repository_full_name, pull_request_number, head_sha);
CREATE INDEX ix_review_requests_repo_pr
    ON review_requests (repo_full_name, pr_number);

-- ❌ 잘못된 인덱스 명명
CREATE INDEX review_requests_idx ON review_requests (repo_full_name);
CREATE INDEX idx_review ON review_requests (repo_full_name);
```

---

## 8. 생성자 주입

모든 의존성은 생성자 주입을 사용한다. 필드 주입(`@Autowired`) 금지.

```kotlin
// ✅ 생성자 주입 (Kotlin 기본 패턴)
@Service
class DefaultReviewService(
    private val aiReviewPort: AiReviewPort,
    private val gitHubApiPort: GitHubApiPort,
) : ReviewUseCase

// ❌ 필드 주입 — 금지
@Service
class DefaultReviewService : ReviewUseCase {
    @Autowired
    private lateinit var aiReviewPort: AiReviewPort
}
```
