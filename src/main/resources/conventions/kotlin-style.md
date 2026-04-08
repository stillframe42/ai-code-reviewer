# Kotlin 코딩 스타일 가이드

이 문서는 프로젝트의 Kotlin 코드 품질 기준을 정의한다.
코드 리뷰 시 이 가이드에 위배되는 패턴은 지적 대상이다.

---

## 1. 네이밍 규칙

### 1.1 클래스 및 인터페이스

파스칼케이스(PascalCase)를 사용한다.

```kotlin
// ✅ 올바른 클래스명
class ReviewRequestEntity
interface ReviewUseCase
data class CodeIssue

// ❌ 잘못된 클래스명
class review_request_entity
class reviewRequestEntity
```

- 인터페이스는 형용사 또는 명사로 명명한다 (`Serializable`, `ReviewUseCase`)
- 구현체는 `Default` 접두사를 사용한다 (`DefaultReviewService`)
- 어댑터 구현체는 기술명 접두사를 사용한다 (`SpringAiReviewAdapter`)

### 1.2 함수 및 변수

카멜케이스(camelCase)를 사용한다.

```kotlin
// ✅ 올바른 함수/변수명
fun processReview(pullRequest: PullRequest): ReviewResult
val repositoryFullName: String
var toolCallCount: Int

// ❌ 잘못된 함수/변수명
fun ProcessReview(pull_request: PullRequest): ReviewResult
val repository_full_name: String
```

- Boolean 변수는 `is`, `has`, `can` 접두사를 사용한다
- 함수명은 동사로 시작한다 (`calculate`, `process`, `find`)

```kotlin
// ✅ Boolean 네이밍
val isProcessing: Boolean
val hasErrors: Boolean
fun canRetry(): Boolean

// ❌ 불명확한 Boolean
val processing: Boolean
val errors: Boolean
```

### 1.3 상수

최상위 또는 companion object의 상수는 대문자 스네이크케이스를 사용한다.

```kotlin
// ✅ 상수 네이밍
companion object {
    const val MAX_RETRY_COUNT = 3
    const val DEFAULT_TIMEOUT_SECONDS = 30
    val SUPPORTED_EXTENSIONS = setOf("kt", "java", "py")
}

// ❌ 잘못된 상수 네이밍
companion object {
    const val maxRetryCount = 3
    const val defaultTimeoutSeconds = 30
}
```

### 1.4 패키지명

소문자만 사용하고 단어를 붙여 쓴다. 언더스코어 금지.

```kotlin
// ✅ 올바른 패키지명
package stillframe42.aicodereviewer.review.adapter.out.persistence

// ❌ 잘못된 패키지명
package stillframe42.aiCodeReviewer.review.adapter.out.persistence
package stillframe42.ai_code_reviewer.review
```

---

## 2. Import 규칙

### 2.1 와일드카드 import 금지

모든 클래스는 명시적으로 import한다.

```kotlin
// ✅ 명시적 import
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.review.domain.model.CodeIssue

// ❌ 와일드카드 import — 금지
import java.util.concurrent.atomic.*
import org.springframework.stereotype.*
```

### 2.2 FQCN(완전 한정 이름) 직접 사용 금지

타입을 사용하는 위치에서 패키지 경로를 직접 작성하지 않는다.

```kotlin
// ✅ import 후 단순 타입명 사용
import java.util.concurrent.atomic.AtomicInteger
fun getCounter(): AtomicInteger = AtomicInteger(0)

// ❌ FQCN 직접 사용 — 금지
fun getCounter(): java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0)
```

---

## 3. 어노테이션 Use-Site Target

### 3.1 생성자 파라미터 어노테이션

Kotlin 생성자 파라미터에 Java 어노테이션을 붙일 때 use-site target을 명시한다.
명시하지 않으면 KT-73255 경고가 발생한다.

```kotlin
// ✅ @param: use-site target 명시
@Component
class GitHubAppTokenProvider(
    @param:Value("${github.app.app-id}")
    private val appId: Long,
    @param:Qualifier("githubWebClient")
    private val webClient: WebClient,
)

// ❌ use-site target 미명시 — 경고 발생
@Component
class GitHubAppTokenProvider(
    @Value("${github.app.app-id}")
    private val appId: Long,
)
```

### 3.2 Use-Site Target 종류

| 상황 | use-site target | 예시 |
|------|----------------|------|
| `@Value`, `@Qualifier` 생성자 주입 | `@param:` | `@param:Value("${some.key}")` |
| Jackson 직렬화 필드 | `@field:` | `@field:JsonProperty("user_id")` |
| Getter 기반 어노테이션 | `@get:` | `@get:JsonIgnore` |
| JPA 컬럼 어노테이션 | `@field:` | `@field:Column(name = "created_at")` |

```kotlin
// ✅ Jackson 필드 어노테이션
data class WebhookPayload(
    @field:JsonProperty("action")
    val action: String,
    @field:JsonProperty("pull_request")
    val pullRequest: PullRequestDto,
)

// ✅ JPA 필드 어노테이션
@Entity
class ReviewRequestEntity(
    @field:Column(name = "repo_full_name", nullable = false)
    val repoFullName: String,
)
```

---

## 4. Null 안전성

### 4.1 Non-null 타입 우선

가능하면 nullable 타입을 피하고 non-null 타입을 사용한다.

```kotlin
// ✅ Non-null 타입 우선
data class ReviewRequest(
    val repoFullName: String,
    val prNumber: Int,
)

// ❌ 불필요한 nullable
data class ReviewRequest(
    val repoFullName: String?,
    val prNumber: Int?,
)
```

### 4.2 `!!` 연산자 사용 자제

강제 언박싱(`!!`)은 런타임 NPE를 유발한다. 대신 안전한 대안을 사용한다.

```kotlin
// ✅ 안전한 대안
val name = user?.name ?: "Anonymous"
val result = list.firstOrNull() ?: return
requireNotNull(value) { "value must not be null" }

// ❌ 강제 언박싱 — 지양
val name = user!!.name
val result = list.first()!!
```

### 4.3 Elvis 연산자로 조기 반환

Null 체크 후 조기 반환 패턴은 Elvis 연산자를 활용한다.

```kotlin
// ✅ Elvis 조기 반환
fun processReview(event: PullRequestEvent?): ReviewResult {
    val pr = event?.pullRequest ?: return ReviewResult.empty()
    val repo = pr.repository ?: return ReviewResult.empty()
    // 실제 처리 로직
}

// ❌ 중첩 if-null 체크
fun processReview(event: PullRequestEvent?): ReviewResult {
    if (event == null) return ReviewResult.empty()
    if (event.pullRequest == null) return ReviewResult.empty()
    if (event.pullRequest.repository == null) return ReviewResult.empty()
    // 실제 처리 로직
}
```

### 4.4 `let`, `also`, `run` 활용

Scope 함수를 활용하여 null 안전 처리를 간결하게 작성한다.

```kotlin
// ✅ let으로 null 안전 처리
user?.let { sendEmail(it.email) }

// ✅ also로 사이드 이펙트
val result = createReview().also {
    logger.info("리뷰 생성 완료: ${it.id}")
}

// ❌ 불필요한 null 체크
if (user != null) {
    sendEmail(user.email)
}
```

---

## 5. 코루틴 사용 관례

### 5.1 단일 값 반환: suspend fun

하나의 값을 비동기로 반환하는 함수는 `suspend fun`으로 선언한다.

```kotlin
// ✅ suspend fun으로 단일 값 반환
suspend fun fetchPrFiles(prNumber: Int): List<PrFile>
suspend fun submitReview(review: PrReview): Long
suspend fun getInstallationToken(installationId: Long): String

// ❌ CompletableFuture 사용 금지
fun fetchPrFiles(prNumber: Int): CompletableFuture<List<PrFile>>

// ❌ @Async 사용 금지
@Async
fun fetchPrFiles(prNumber: Int): Future<List<PrFile>>
```

### 5.2 스트리밍 값 반환: Flow<T>

여러 값을 순차적으로 방출하는 경우 `Flow<T>`를 반환한다.

```kotlin
// ✅ Flow로 스트리밍 반환
fun streamChatResponse(message: String): Flow<String>
fun observeReviewProgress(requestId: Long): Flow<ReviewStatus>

// ❌ Observable/Flux 사용 금지 (코루틴으로 통일)
fun streamChatResponse(message: String): Flux<String>
```

### 5.3 Controller에서 suspend fun 사용

Spring MVC + `kotlinx-coroutines-reactor` 브릿지를 통해 Controller에서 직접 suspend fun을 사용할 수 있다.

```kotlin
// ✅ Controller에서 suspend fun 직접 사용
@RestController
class ReviewController(private val reviewUseCase: ReviewUseCase) {
    @PostMapping("/api/review")
    suspend fun requestReview(@RequestBody request: ReviewRequest): ReviewResponse {
        return reviewUseCase.requestReview(request)
    }
}

// ❌ 불필요한 runBlocking 래핑
@PostMapping("/api/review")
fun requestReview(@RequestBody request: ReviewRequest): ReviewResponse {
    return runBlocking { reviewUseCase.requestReview(request) }
}
```

### 5.4 Duration 사용 규칙

시간 인자를 받는 코루틴 함수는 `Duration` 오버로드를 사용한다.

```kotlin
// ✅ Duration 오버로드 사용
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

withTimeout(10.seconds)
delay(500.milliseconds)
withContext(Dispatchers.IO) {
    withTimeout(30.seconds) {
        fetchFromExternalApi()
    }
}

// ❌ Long milliseconds 오버로드 — deprecated 경고 발생
withTimeout(10_000L)
delay(500L)
```

---

## 6. Data Class 설계

### 6.1 도메인 모델에 data class 사용

순수 도메인 모델과 DTO는 `data class`로 선언한다.

```kotlin
// ✅ data class로 도메인 모델
data class CodeIssue(
    val file: String,
    val line: Int,
    val category: IssueCategory,
    val severity: IssueSeverity,
    val message: String,
    val suggestion: String? = null,
)

// ✅ data class로 DTO
data class ReviewRequest(
    val repoFullName: String,
    val prNumber: Int,
    val headSha: String,
)
```

### 6.2 JPA Entity에 data class 사용 금지

JPA Entity는 `data class`가 아닌 일반 `class`로 선언한다.
`data class`의 `equals`/`hashCode`는 JPA 프록시와 충돌한다.

```kotlin
// ✅ Entity는 일반 class
@Entity
@Table(name = "review_requests")
class ReviewRequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val repoFullName: String,
    val prNumber: Int,
)

// ❌ Entity에 data class 금지
@Entity
data class ReviewRequestEntity(
    @Id val id: Long = 0,
    val repoFullName: String,
)
```

### 6.3 copy() 활용

`data class`의 `copy()`를 활용하여 불변 객체를 변경한다.

```kotlin
// ✅ copy()로 불변 업데이트
val updatedRequest = reviewRequest.copy(status = ReviewRequestStatus.DONE)

// ❌ 가변 필드로 변경
reviewRequest.status = ReviewRequestStatus.DONE  // var 사용 — 지양
```

---

## 7. 확장 함수 설계

### 7.1 확장 함수 사용 기준

- 외부 라이브러리 클래스에 기능을 추가할 때 사용
- 특정 타입에만 의미 있는 유틸리티 함수에 사용
- 클래스 내부 상태에 접근할 필요가 없을 때 사용

```kotlin
// ✅ 확장 함수 적절한 사용
fun String.toSnakeCase(): String =
    replace(Regex("([A-Z])"), "_$1").lowercase().trimStart('_')

fun List<CodeIssue>.countBySeverity(severity: IssueSeverity): Int =
    count { it.severity == severity }

// ✅ 특정 타입의 변환 로직
fun PullRequestEvent.toDomain(): PullRequest =
    PullRequest(
        number = this.pullRequest.number,
        headSha = this.pullRequest.head.sha,
    )
```

### 7.2 확장 함수 위치

- 단일 파일에서만 사용: 해당 파일 내 private 확장 함수
- 여러 파일에서 사용: 전용 확장 함수 파일 (`Extensions.kt`)

```kotlin
// ✅ 파일 내 private 확장 함수
private fun WebhookPayloadDto.toDomain(): PullRequestEvent = ...

// ✅ 공유 확장 함수는 별도 파일에
// StringExtensions.kt
fun String.truncate(maxLength: Int): String =
    if (length <= maxLength) this else substring(0, maxLength) + "..."
```

---

## 8. Sealed Class와 When 표현식

### 8.1 Sealed Class 정의

상태나 결과를 표현할 때 sealed class를 사용한다.

```kotlin
// ✅ sealed class로 결과 표현
sealed class ReviewResult {
    data class Success(val review: CodeReview) : ReviewResult()
    data class Failure(val reason: String) : ReviewResult()
    object Skipped : ReviewResult()
}

// ✅ sealed interface 활용 (Kotlin 1.5+)
sealed interface AiResponse {
    data class Text(val content: String) : AiResponse
    data class ToolCall(val name: String, val args: String) : AiResponse
}
```

### 8.2 When 표현식은 exhaustive하게

`when`은 모든 케이스를 처리하는 표현식으로 사용한다.
`else` 브랜치 없이 sealed class의 모든 하위 타입을 명시한다.

```kotlin
// ✅ exhaustive when — else 없이 모든 케이스 처리
fun handleResult(result: ReviewResult): String = when (result) {
    is ReviewResult.Success -> "리뷰 완료: ${result.review.summary}"
    is ReviewResult.Failure -> "리뷰 실패: ${result.reason}"
    ReviewResult.Skipped -> "리뷰 건너뜀"
}

// ❌ else로 케이스 누락 — 새 하위 타입 추가 시 버그 위험
fun handleResult(result: ReviewResult): String = when (result) {
    is ReviewResult.Success -> "완료"
    else -> "기타"  // 새 케이스 추가 시 조용히 무시됨
}
```

---

## 9. 컬렉션 API 관용 패턴

### 9.1 함수형 체이닝 선호

컬렉션 변환은 함수형 API를 체이닝하여 표현한다.

```kotlin
// ✅ 함수형 체이닝
val criticalIssues = issues
    .filter { it.severity == IssueSeverity.CRITICAL }
    .sortedByDescending { it.line }
    .take(10)
    .map { it.message }

// ❌ 명령형 루프
val criticalIssues = mutableListOf<String>()
for (issue in issues) {
    if (issue.severity == IssueSeverity.CRITICAL) {
        criticalIssues.add(issue.message)
    }
}
```

### 9.2 적절한 컬렉션 타입 선택

```kotlin
// ✅ 불변 컬렉션 기본 사용
val files: List<PrFile> = fetchPrFiles()
val extensions: Set<String> = setOf("kt", "java", "py")
val config: Map<String, String> = mapOf("key" to "value")

// ✅ 가변 컬렉션은 필요한 경우만
fun buildResult(): List<CodeIssue> {
    val issues = mutableListOf<CodeIssue>()
    // ... 빌드 로직
    return issues.toList()  // 반환 시 불변으로 변환
}
```

### 9.3 시퀀스(Sequence) 활용

대용량 컬렉션 처리 시 지연 평가를 위해 `asSequence()`를 사용한다.

```kotlin
// ✅ 대용량 처리에 Sequence
val summary = largeFileList
    .asSequence()
    .filter { it.status != PrFileStatus.REMOVED }
    .map { it.filename }
    .joinToString(", ")

// ❌ 중간 컬렉션 생성 비용 발생
val summary = largeFileList
    .filter { it.status != PrFileStatus.REMOVED }
    .map { it.filename }
    .joinToString(", ")
```

---

## 10. 표현식 함수 (Expression Body)

단일 표현식 함수는 `=`을 사용하여 간결하게 작성한다.

```kotlin
// ✅ 표현식 함수
fun isKotlinFile(filename: String): Boolean = filename.endsWith(".kt")

fun toReviewStatus(status: String): ReviewRequestStatus =
    ReviewRequestStatus.valueOf(status.uppercase())

// ✅ 복잡한 경우는 블록 함수 사용
fun processFiles(files: List<PrFile>): ProcessedResult {
    val filtered = files.filter { it.isReviewable() }
    val grouped = filtered.groupBy { it.extension }
    return ProcessedResult(grouped)
}

// ❌ 단순 반환에 불필요한 블록
fun isKotlinFile(filename: String): Boolean {
    return filename.endsWith(".kt")
}
```

---

## 11. 함수 파라미터 기본값과 Named Arguments

### 11.1 기본값으로 오버로드 대체

함수 오버로드 대신 기본값을 활용한다.

```kotlin
// ✅ 기본값 활용
fun searchIssues(
    severity: IssueSeverity = IssueSeverity.ALL,
    maxResults: Int = 10,
    includeResolved: Boolean = false,
): List<CodeIssue>

// ❌ 불필요한 오버로드
fun searchIssues(): List<CodeIssue>
fun searchIssues(severity: IssueSeverity): List<CodeIssue>
fun searchIssues(severity: IssueSeverity, maxResults: Int): List<CodeIssue>
```

### 11.2 Named Arguments로 가독성 향상

파라미터가 3개 이상이거나 Boolean/숫자 파라미터가 있을 때 named arguments를 사용한다.

```kotlin
// ✅ Named arguments
val issue = CodeIssue(
    file = "ReviewService.kt",
    line = 42,
    category = IssueCategory.SECURITY,
    severity = IssueSeverity.CRITICAL,
    message = "SQL Injection 취약점 발견",
)

// ❌ Positional arguments — 의미 파악 어려움
val issue = CodeIssue("ReviewService.kt", 42, IssueCategory.SECURITY, IssueSeverity.CRITICAL, "SQL Injection 취약점 발견")
```

---

## 12. 주석 작성 규칙

### 12.1 한국어 주석

모든 주석은 한국어로 작성한다.

```kotlin
// ✅ 한국어 주석
// PR 이벤트 중복 처리 방지 — (레포, PR번호, SHA) 조합으로 유니크 판별
fun isAlreadyProcessed(event: PullRequestEvent): Boolean

// ❌ 영어 주석
// Check if the PR event has already been processed
fun isAlreadyProcessed(event: PullRequestEvent): Boolean
```

### 12.2 자명한 코드에 주석 금지

코드 자체로 의미를 파악할 수 있으면 주석을 달지 않는다.

```kotlin
// ✅ 주석 없이 자명한 코드
fun isKotlinFile(filename: String): Boolean = filename.endsWith(".kt")

// ❌ 불필요한 주석
// 파일명이 .kt로 끝나는지 확인한다
fun isKotlinFile(filename: String): Boolean = filename.endsWith(".kt")

// ✅ 비자명한 로직에는 주석 필요
// GitHub API rate limit: 토큰당 시간당 5,000 요청
// 병렬 호출 수를 제한하여 초과 방지
val maxConcurrency = 3
```
