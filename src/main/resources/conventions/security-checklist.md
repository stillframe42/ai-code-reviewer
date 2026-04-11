# 보안 체크리스트

이 문서는 코드 리뷰 시 반드시 확인해야 하는 보안 사항을 정의한다.
OWASP Top 10 (2021)을 기준으로 Spring Boot 환경에서의 대응 방법을 포함한다.

---

## 1. OWASP Top 10 (2021) 체크리스트

### A01: 접근 제어 취약점 (Broken Access Control)

**위험:** 인증된 사용자가 권한 없는 리소스에 접근할 수 있는 취약점

**Spring Boot 대응:**

```kotlin
// ✅ 메서드 수준 권한 검사
@PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
fun getUserData(userId: Long): UserData

// ✅ 리소스 소유권 확인
fun getReview(reviewId: Long, currentUserId: Long): Review {
    val review = reviewRepository.findById(reviewId)
        ?: throw ReviewNotFoundException(reviewId)
    require(review.ownerId == currentUserId) { "접근 권한 없음" }
    return review
}

// ❌ 권한 검사 누락
fun deleteReview(reviewId: Long) {
    reviewRepository.deleteById(reviewId)  // ← 누가 삭제하는지 확인 안 함
}
```

**체크 항목:**
- [ ] 모든 API 엔드포인트에 인증/인가 적용 여부
- [ ] 사용자가 타인의 리소스에 접근 가능한지 확인
- [ ] 디렉토리 순회(path traversal) 취약점 여부

### A02: 암호화 실패 (Cryptographic Failures)

**위험:** 민감 데이터가 평문으로 저장되거나 전송되는 취약점

**Spring Boot 대응:**

```kotlin
// ✅ 비밀번호 해싱
@Bean
fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

// ✅ 민감 필드 로깅 제외
data class UserCredential(
    val username: String,
    @field:JsonIgnore
    val password: String,  // 직렬화 제외
) {
    override fun toString(): String = "UserCredential(username=$username)"  // 비밀번호 제외
}

// ❌ 평문 비밀번호 저장 — 절대 금지
userRepository.save(User(username = username, password = password))

// ❌ 로그에 민감 정보 출력 — 절대 금지
logger.info("사용자 인증: $username / $password")
```

**체크 항목:**
- [ ] 비밀번호, API 키는 반드시 해시/암호화 저장
- [ ] HTTPS 사용 (HTTP 금지)
- [ ] 민감 데이터가 로그에 출력되지 않는지 확인
- [ ] API 키, 시크릿이 소스 코드에 하드코딩되지 않는지 확인

### A03: 인젝션 (Injection)

**위험:** SQL Injection, Command Injection 등 외부 입력이 실행 컨텍스트에 주입되는 취약점

**Injection 공격 방어의 핵심 원칙:**
1. **Parameterized Query (파라미터 바인딩)** — 외부 입력을 SQL/JPQL 문자열에 직접 연결하지 않고 바인딩 변수로 처리한다
2. **Input Validation (입력값 검증)** — 외부 입력을 처리하기 전에 Bean Validation으로 형식과 범위를 검증한다
3. **출력 인코딩** — 데이터를 출력할 컨텍스트(HTML, 로그 등)에 맞게 이스케이프한다

**Spring Boot 대응 — SQL Injection 방어 (Parameterized Query):**

```kotlin
// ✅ JPA 메서드명 기반 쿼리 — 파라미터 바인딩 자동 적용 (Prepared Statement)
fun findByRepoAndPr(repoFullName: String, prNumber: Int): ReviewRequest? =
    reviewRepository.findByRepoFullNameAndPrNumber(repoFullName, prNumber)

// ✅ @Query에서 파라미터 바인딩 — :param 형식으로 Prepared Statement 사용
@Query("SELECT r FROM ReviewRequestEntity r WHERE r.repoFullName = :repo AND r.prNumber = :pr")
fun findByRepoPr(@Param("repo") repo: String, @Param("pr") pr: Int): ReviewRequestEntity?

// ✅ JdbcTemplate 파라미터 바인딩 — ? 플레이스홀더 사용
jdbcTemplate.queryForObject(
    "SELECT count(*) FROM vector_store WHERE metadata->>'source' = ?",
    Long::class.java,
    source,
)

// ❌ SQL 문자열 직접 조합 — SQL Injection 취약점
val query = "SELECT * FROM reviews WHERE repo = '$repoFullName'"
jdbcTemplate.query(query, ...)

// ❌ 셸 명령에 외부 입력 사용 — Command Injection 취약점
Runtime.getRuntime().exec("git clone $userInput")
```

**Spring Boot 대응 — Input Validation (입력값 검증):**

```kotlin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated

// ✅ Bean Validation으로 입력값 검증 — Injection 방어 1차 방어선
// 형식/범위가 맞지 않는 입력은 컨트롤러 진입 전에 400으로 차단된다
data class ReviewRequest(
    @field:NotBlank val repoFullName: String,
    @field:Min(1) val prNumber: Int,
)

@PostMapping("/api/review")
suspend fun requestReview(
    @Valid @RequestBody request: ReviewRequest,  // @Valid 누락 시 검증 미적용
): ReviewResponse

// ❌ 입력값 검증 없이 쿼리 실행
fun search(keyword: String) =
    jdbcTemplate.query("SELECT * FROM docs WHERE content LIKE '%$keyword%'", ...)
```

**Spring Boot 대응 — Log Injection / XSS 방어 (출력 인코딩):**

```kotlin
import org.springframework.web.util.HtmlUtils

// ✅ Log Injection 방어 — 사용자 입력의 개행 문자 제거
fun sanitizeForLog(input: String): String =
    input.replace("\n", "\\n").replace("\r", "\\r")

logger.info("PR 요청: ${sanitizeForLog(userInput)}")

// ❌ Log Injection — 사용자 입력이 로그를 오염시킴
logger.info("PR 요청: $userInput")  // userInput에 개행이 있으면 로그 위조 가능

// ✅ XSS 방어 — HTML 출력 시 이스케이프
val safeOutput = HtmlUtils.htmlEscape(userInput)

// ❌ XSS — HTML에 사용자 입력 그대로 삽입
"<div>$userInput</div>"
```

**Injection 공격 방어 전략 요약:**

| Injection 유형 | 방어 방법 |
|---------------|---------|
| SQL Injection | JPA 파라미터 바인딩, `@Query` + `@Param`, JdbcTemplate `?` 플레이스홀더 |
| JPQL Injection | `@Query` + `:param` 바인딩 (문자열 연결 금지) |
| Command Injection | 셸 명령에 외부 입력 사용 금지 |
| Log Injection | 개행 문자(`\n`, `\r`) 이스케이프 후 로깅 |
| XSS | `HtmlUtils.htmlEscape()`, Content-Type 헤더 명시 |

**체크 항목:**
- [ ] 모든 DB 쿼리에 파라미터 바인딩(Prepared Statement) 사용 — 문자열 직접 조합 금지
- [ ] 모든 API 입력에 `@Valid` + Bean Validation 어노테이션 적용
- [ ] 동적 쿼리 생성 시 외부 입력을 SQL/JPQL 문자열에 직접 포함하지 않는지 확인
- [ ] 외부 입력으로 셸 명령 실행 금지
- [ ] 로그 출력 전 사용자 입력의 개행 문자 제거

### A04: 안전하지 않은 설계 (Insecure Design)

**위험:** 보안이 설계 단계에서 고려되지 않은 취약점

**Spring Boot 대응:**

```kotlin
// ✅ Rate Limiting 적용
@Bean
fun rateLimiter(): RateLimiter = RateLimiter.create(10.0)  // 초당 10회

// ✅ 입력값 유효성 검증
@PostMapping("/api/review")
suspend fun requestReview(
    @Valid @RequestBody request: ReviewRequest,  // Bean Validation
): ReviewResponse

// ✅ 민감 API에 추가 인증
@PostMapping("/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
suspend fun createUser(@RequestBody request: CreateUserRequest)
```

**체크 항목:**
- [ ] 공개 API에 Rate Limiting 적용
- [ ] 관리자 기능에 추가 인증 레이어
- [ ] 실패 시나리오에 대한 보안 처리

### A05: 보안 설정 오류 (Security Misconfiguration)

**위험:** 기본 설정, 불필요한 기능 활성화, 디버그 정보 노출

**Spring Boot 대응:**

```yaml
# ✅ 운영 환경 Actuator 제한
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus  # 필요한 것만 노출
  server:
    port: 8081  # 별도 포트로 분리

# ❌ 모든 Actuator 엔드포인트 노출 — 금지
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

```kotlin
// ✅ 운영 환경 스택 트레이스 숨김
@ExceptionHandler(Exception::class)
fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
    logger.error("내부 오류 발생", e)
    return ResponseEntity.status(500).body(
        ErrorResponse(error = "서버 내부 오류가 발생했습니다.")  // 상세 메시지 숨김
    )
}

// ❌ 클라이언트에 스택 트레이스 노출
return ResponseEntity.status(500).body(
    ErrorResponse(error = e.stackTraceToString())  // 내부 정보 노출 — 금지
)
```

**체크 항목:**
- [ ] Actuator 엔드포인트 최소한으로 노출
- [ ] 오류 응답에 스택 트레이스 미포함
- [ ] CORS 설정이 과도하게 허용적이지 않은지 확인
- [ ] 불필요한 HTTP 메서드 비활성화

### A06: 취약하거나 오래된 컴포넌트 (Vulnerable and Outdated Components)

**위험:** 알려진 취약점이 있는 라이브러리 사용

**Spring Boot 대응:**

```bash
# 의존성 취약점 스캔
./gradlew dependencyCheckAnalyze

# 의존성 업데이트 확인
./gradlew dependencyUpdates
```

**체크 항목:**
- [ ] Spring Boot, Spring Security 최신 패치 버전 사용
- [ ] 알려진 CVE가 있는 라이브러리 사용 여부
- [ ] 더 이상 지원하지 않는(EOL) 라이브러리 사용 여부

### A07: 인증 및 인증 실패 (Identification and Authentication Failures)

**위험:** 취약한 인증 메커니즘, 세션 관리 오류

**Spring Boot 대응:**

```kotlin
// ✅ JWT 토큰 검증
@Component
class JwtAuthFilter(private val jwtSigner: JwtSigner) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, ...) {
        val token = extractToken(request) ?: return chain.doFilter(request, response)
        val claims = jwtSigner.verify(token)  // 서명 검증
        // 만료, 발급자, 대상 검증
    }
}

// ✅ 로그인 실패 횟수 제한
@Service
class AuthService {
    private val failedAttempts = ConcurrentHashMap<String, Int>()

    fun login(username: String, password: String): AuthToken {
        val attempts = failedAttempts.getOrDefault(username, 0)
        require(attempts < 5) { "계정이 잠겼습니다. 나중에 다시 시도하세요." }
        // 인증 처리
    }
}
```

**체크 항목:**
- [ ] 비밀번호 복잡도 요구사항 적용
- [ ] 로그인 실패 횟수 제한
- [ ] JWT 토큰 만료 시간 적절히 설정 (Access: 15분, Refresh: 7일)
- [ ] 토큰 무효화 처리 (로그아웃 시)

### A08: 소프트웨어 및 데이터 무결성 실패 (Software and Data Integrity Failures)

**위험:** 신뢰할 수 없는 소스에서 코드 또는 데이터를 가져오는 취약점

**Spring Boot 대응:**

```kotlin
// ✅ Webhook 서명 검증 (이 프로젝트의 GitHub Webhook 처리)
@Component
class HmacSignatureVerifier {
    fun verify(payload: ByteArray, signature: String, secret: String): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val expected = "sha256=" + mac.doFinal(payload).toHex()
        return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    }
}

// ❌ 서명 검증 없이 Webhook 처리 — 금지
@PostMapping("/webhook")
fun handleWebhook(@RequestBody payload: String) {
    processEvent(payload)  // 서명 검증 없음 — 누구든 이벤트를 발생시킬 수 있음
}
```

**체크 항목:**
- [ ] Webhook 수신 시 서명 검증
- [ ] 외부 라이브러리 체크섬 검증 (Gradle dependency verification)
- [ ] 역직렬화 시 타입 검증

### A09: 보안 로깅 및 모니터링 실패 (Security Logging and Monitoring Failures)

**위험:** 보안 이벤트가 기록되지 않아 침해 사실을 늦게 감지

**Spring Boot 대응:**

```kotlin
// ✅ 보안 이벤트 로깅
@Service
class AuthService(private val logger: Logger) {

    fun login(username: String, password: String): AuthToken {
        return try {
            val token = authenticate(username, password)
            logger.info("로그인 성공: username={}", username)
            token
        } catch (e: AuthenticationException) {
            logger.warn("로그인 실패: username={}, 이유={}", username, e.message)
            throw e
        }
    }
}

// ✅ 민감 작업 감사 로그
fun deleteUser(userId: Long, adminId: Long) {
    logger.warn("사용자 삭제: targetUserId={}, adminId={}, timestamp={}", userId, adminId, Instant.now())
    userRepository.deleteById(userId)
}
```

**체크 항목:**
- [ ] 인증 성공/실패 로깅
- [ ] 권한 거부 이벤트 로깅
- [ ] 민감 작업(삭제, 권한 변경) 감사 로그
- [ ] 로그에 개인정보(비밀번호, 카드번호) 미포함

### A10: 서버 사이드 요청 위조 (SSRF)

**위험:** 서버가 공격자가 지정한 URL로 요청을 보내는 취약점

**Spring Boot 대응:**

```kotlin
// ✅ 허용 도메인 화이트리스트
private val allowedHosts = setOf("api.github.com", "api.anthropic.com")

fun fetchExternalResource(url: String): String {
    val uri = URI(url)
    require(uri.host in allowedHosts) { "허용되지 않은 도메인: ${uri.host}" }
    return webClient.get().uri(uri).retrieve().bodyToMono<String>().awaitSingle()
}

// ❌ URL 검증 없이 외부 요청 — SSRF 취약점
fun fetchExternalResource(url: String): String {
    return webClient.get().uri(url).retrieve().bodyToMono<String>().awaitSingle()
}
```

---

## 2. Spring Security 설정 체크리스트

### 2.1 CSRF 설정

REST API는 CSRF 토큰 방식 대신 JWT/API 키 인증을 사용하므로 CSRF를 비활성화할 수 있다.

```kotlin
// ✅ REST API에서 CSRF 비활성화 (JWT 사용 시)
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }  // JWT 인증 사용 시 비활성화
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/api/health", permitAll)
                authorize("/actuator/**", hasRole("ADMIN"))
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }
}
```

### 2.2 CORS 설정

```kotlin
// ✅ 특정 오리진만 허용
@Bean
fun corsConfigurationSource(): CorsConfigurationSource {
    val config = CorsConfiguration()
    config.allowedOrigins = listOf("https://your-frontend.com")  // 특정 도메인만
    config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE")
    config.allowedHeaders = listOf("Authorization", "Content-Type")
    config.allowCredentials = true

    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/api/**", config)
    return source
}

// ❌ 모든 오리진 허용 — 금지 (운영 환경에서)
config.allowedOrigins = listOf("*")
config.allowCredentials = true  // allowedOrigins = "*"와 함께 사용 불가
```

### 2.3 민감 정보 관리

```yaml
# ✅ application-secret.yml (git 추적 제외)
spring:
  datasource:
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
github:
  app:
    private-key-path: ${GITHUB_PRIVATE_KEY_PATH}
anthropic:
  api-key: ${ANTHROPIC_API_KEY}

# ❌ application.yml에 시크릿 하드코딩 — 절대 금지
spring:
  datasource:
    password: my-secret-password
```

```
# ✅ .gitignore에 시크릿 파일 추가
application-secret.yml
secrets/
*.pem
*.key
```

---

## 3. 로깅 보안 패턴

### 3.1 개인정보(PII) 마스킹

로그에 개인정보가 출력되지 않도록 마스킹 또는 제외 처리한다.

```kotlin
// ✅ toString()에서 민감 필드 제외
data class UserInfo(
    val userId: Long,
    val email: String,
    val phoneNumber: String,
) {
    override fun toString(): String = "UserInfo(userId=$userId)"  // 이메일/전화번호 제외
}

// ✅ 필요한 경우 마스킹 처리
fun String.maskEmail(): String {
    val atIndex = indexOf('@')
    if (atIndex <= 1) return "***"
    return "${first()}***${substring(atIndex)}"  // "h***@example.com"
}

fun String.maskPhone(): String =
    replace(Regex("(\\d{3})-?(\\d{3,4})-?(\\d{4})"), "$1-****-$3")  // "010-****-5678"

// ❌ 개인정보 그대로 로깅 — 절대 금지
logger.info("사용자 로그인: email=$email, phone=$phoneNumber")
```

### 3.2 MDC 활용 시 민감 정보 제외

SLF4J MDC(Mapped Diagnostic Context)에 추적용 ID는 포함하되 민감 데이터는 제외한다.

```kotlin
// ✅ 요청 추적용 ID만 MDC에 등록
MDC.put("requestId", requestId)
MDC.put("userId", userId.toString())  // 식별자 OK

// ❌ MDC에 민감 데이터 등록 — 모든 로그에 노출됨
MDC.put("userEmail", email)      // 개인정보 금지
MDC.put("authToken", token)      // 인증 정보 금지
```

### 3.3 로그 레벨별 개인정보 정책

| 레벨 | 허용 | 금지 |
|------|------|------|
| `ERROR` | 예외 메시지, 스택 트레이스 | 사용자 입력값, PII |
| `WARN` | 비정상 상황 설명 | 사용자 데이터 |
| `INFO` | 이벤트 발생 사실, 식별자 ID | 이메일, 전화번호, 토큰 |
| `DEBUG` | 내부 상태 (운영 환경 비활성화) | 비밀번호, 카드번호 |
