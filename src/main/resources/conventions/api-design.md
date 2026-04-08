# API 설계 가이드

이 문서는 REST API 설계와 OpenAPI 명세 작성 규칙을 정의한다.
새 API를 추가하거나 기존 API를 변경할 때 이 가이드를 준수한다.

---

## 1. OpenAPI 3.1 명세 작성 규칙

### 1.1 필수 메타데이터

모든 엔드포인트에는 다음 항목을 반드시 작성한다.

```yaml
# ✅ 필수 메타데이터 포함
paths:
  /api/review:
    post:
      operationId: requestReview          # 고유 식별자 — camelCase
      summary: PR 코드 리뷰 요청           # 한 줄 요약
      description: |                      # 상세 설명
        GitHub PR의 변경 파일을 분석하여 AI 코드 리뷰를 생성합니다.
        리뷰 요청은 비동기로 처리됩니다.
      tags:
        - review                          # 그룹화 태그

# ❌ 메타데이터 누락 — 금지
paths:
  /api/review:
    post:
      requestBody: ...
      responses: ...
```

### 1.2 스키마 $ref 참조 규칙

모든 스키마는 `components/schemas`에 정의하고 `$ref`로 참조한다.
인라인 스키마 작성은 금지한다.

```yaml
# ✅ $ref로 스키마 참조
paths:
  /api/review:
    post:
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ReviewRequest'  # ← 참조
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ReviewResponse'

components:
  schemas:
    ReviewRequest:
      type: object
      required: [repoFullName, prNumber, headSha]
      properties:
        repoFullName:
          type: string
          example: "owner/repo"
        prNumber:
          type: integer
          example: 42

# ❌ 인라인 스키마 — 금지
paths:
  /api/review:
    post:
      requestBody:
        content:
          application/json:
            schema:
              type: object          # ← 인라인 정의 금지
              properties:
                repoFullName:
                  type: string
```

### 1.3 응답 코드 완전 명세

가능한 모든 HTTP 응답 코드에 대한 스키마와 예시를 포함한다.

```yaml
# ✅ 모든 응답 코드 명세
paths:
  /api/review:
    post:
      responses:
        '202':
          description: 리뷰 요청 접수 완료
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ReviewResponse'
              example:
                requestId: 123
                status: "PENDING"
        '400':
          description: 잘못된 요청 파라미터
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ValidationErrorResponse'
              example:
                errors:
                  prNumber: "PR 번호는 양수여야 합니다"
        '500':
          description: 서버 내부 오류
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
              example:
                error: "서버 내부 오류가 발생했습니다"
```

---

## 2. 에러 응답 스키마 설계

### 2.1 ValidationErrorResponse — 필드별 유효성 오류

여러 필드에 걸친 유효성 검증 오류는 `ValidationErrorResponse`를 사용한다.

```yaml
# OpenAPI 스키마
ValidationErrorResponse:
  type: object
  properties:
    errors:
      type: object
      additionalProperties:
        type: string
      example:
        prNumber: "PR 번호는 양수여야 합니다"
        repoFullName: "레포지토리 이름은 필수입니다"
```

```kotlin
// ✅ Spring에서 ValidationErrorResponse 반환
@ExceptionHandler(MethodArgumentNotValidException::class)
fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
    val errors = e.bindingResult.fieldErrors.associate { error ->
        error.field to (error.defaultMessage ?: "유효하지 않은 값")
    }
    return ResponseEntity.badRequest().body(mapOf("errors" to errors))
}
```

### 2.2 ErrorResponse — 단일 메시지 오류

단일 오류 메시지는 `ErrorResponse`를 사용한다.

```yaml
# OpenAPI 스키마
ErrorResponse:
  type: object
  required: [error]
  properties:
    error:
      type: string
      example: "리뷰를 찾을 수 없습니다"
```

```kotlin
// ✅ Spring에서 ErrorResponse 반환
@ExceptionHandler(ReviewNotFoundException::class)
fun handleNotFound(e: ReviewNotFoundException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(404).body(mapOf("error" to e.message!!))
}

@ExceptionHandler(Exception::class)
fun handleGeneral(e: Exception): ResponseEntity<Map<String, String>> {
    logger.error("처리되지 않은 오류", e)
    return ResponseEntity.status(500).body(mapOf("error" to "서버 내부 오류가 발생했습니다"))
}
```

---

## 3. RESTful 리소스 네이밍 규칙

### 3.1 URL 설계 원칙

```
# ✅ 올바른 RESTful URL
GET    /api/reviews              # 리뷰 목록 조회
POST   /api/reviews              # 리뷰 생성
GET    /api/reviews/{id}         # 단건 리뷰 조회
DELETE /api/reviews/{id}         # 리뷰 삭제

GET    /api/repositories/{repo}/reviews    # 레포지토리별 리뷰 목록

# ❌ 잘못된 URL 패턴
POST   /api/createReview         # 동사 사용 금지
GET    /api/getReviewById/{id}   # 동사 사용 금지
POST   /api/review/delete/{id}   # DELETE 메서드 사용
```

### 3.2 URL 형식 규칙

```
# ✅ 소문자 케밥케이스(kebab-case)
/api/pull-requests/{prNumber}/reviews
/api/code-issues
/api/review-requests

# ❌ 카멜케이스 — 금지
/api/pullRequests/{prNumber}/reviews
/api/codeIssues

# ❌ 언더스코어 — 금지
/api/pull_requests/{prNumber}/reviews
```

### 3.3 리소스는 복수형

```
# ✅ 복수형
/api/reviews
/api/repositories
/api/users

# ❌ 단수형
/api/review
/api/repository
```

---

## 4. HTTP 상태 코드 사용 기준

### 4.1 성공 응답

| 코드 | 이름 | 사용 상황 |
|------|------|---------|
| 200 OK | 성공 | GET 조회, PUT 업데이트 성공 |
| 201 Created | 생성 완료 | POST로 리소스 생성 성공 |
| 202 Accepted | 요청 수락 | 비동기 처리 시작 (리뷰 요청 등) |
| 204 No Content | 내용 없음 | DELETE 성공, 응답 바디 없음 |

```kotlin
// ✅ 상황에 맞는 상태 코드
@PostMapping("/api/reviews")
suspend fun requestReview(@RequestBody request: ReviewRequest): ResponseEntity<ReviewResponse> {
    val response = reviewUseCase.requestReview(request)
    return ResponseEntity.status(202).body(response)  // 비동기 처리 → 202
}

@PostMapping("/api/conventions")
suspend fun addConvention(@RequestBody request: ConventionRequest): ResponseEntity<ConventionResponse> {
    val response = conventionUseCase.add(request)
    return ResponseEntity.status(201).body(response)  // 리소스 생성 → 201
}
```

### 4.2 클라이언트 오류

| 코드 | 이름 | 사용 상황 |
|------|------|---------|
| 400 Bad Request | 잘못된 요청 | 유효성 검증 실패, 잘못된 파라미터 |
| 401 Unauthorized | 인증 필요 | 인증 토큰 없음 또는 만료 |
| 403 Forbidden | 권한 없음 | 인증됐으나 권한 부족 |
| 404 Not Found | 리소스 없음 | 존재하지 않는 리소스 |
| 409 Conflict | 충돌 | 중복 리소스 생성 시도 |
| 422 Unprocessable Entity | 처리 불가 | 문법은 맞으나 의미적으로 잘못된 요청 |
| 429 Too Many Requests | 요청 한도 초과 | Rate Limit 초과 |

```kotlin
// ✅ 401 vs 403 구분
// 401: 인증 자체가 없거나 유효하지 않음
throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다")

// 403: 인증은 됐지만 권한이 없음
throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 리소스에 접근할 권한이 없습니다")
```

### 4.3 서버 오류

| 코드 | 이름 | 사용 상황 |
|------|------|---------|
| 500 Internal Server Error | 내부 오류 | 예상치 못한 서버 오류 |
| 502 Bad Gateway | 게이트웨이 오류 | 상위 서비스 오류 |
| 503 Service Unavailable | 서비스 불가 | 과부하, 유지보수 중 |

---

## 5. 응답 형식 통일

### 5.1 페이지네이션 응답

```kotlin
// ✅ 페이지네이션 응답 구조
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)
```

```yaml
# OpenAPI 스키마
ReviewListResponse:
  type: object
  properties:
    content:
      type: array
      items:
        $ref: '#/components/schemas/ReviewSummary'
    page:
      type: integer
    size:
      type: integer
    totalElements:
      type: integer
      format: int64
    totalPages:
      type: integer
    hasNext:
      type: boolean
```

### 5.2 날짜/시간 형식

모든 날짜/시간은 ISO 8601 형식의 UTC로 반환한다.

```kotlin
// ✅ Instant → ISO 8601 UTC 직렬화
data class ReviewResponse(
    val id: Long,
    val createdAt: Instant,  // "2026-04-09T03:00:00Z" 형태로 직렬화
)

// ✅ ObjectMapper 설정
@Bean
fun objectMapper(): ObjectMapper = ObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
```

---

## 6. API 버저닝 전략

### 6.1 URL 경로 버저닝 (채택)

```
# ✅ URL 경로 버저닝
/api/v1/reviews
/api/v2/reviews

# 현재 프로젝트: /api/* (v1 생략, 단일 버전)
```

### 6.2 하위 호환성 유지 원칙

```kotlin
// ✅ 새 필드 추가는 하위 호환 (기존 클라이언트 영향 없음)
data class ReviewResponse(
    val id: Long,
    val status: String,
    val summary: String? = null,         // 기존 필드
    val issueCount: Int = 0,             // 신규 필드 — 기본값으로 하위 호환
)

// ❌ 기존 필드 제거 또는 타입 변경 — 하위 호환성 깨짐
data class ReviewResponse(
    val id: String,    // ← Long → String 타입 변경 — 금지
    // val status: String  ← 기존 필드 제거 — 금지
)
```

### 6.3 Deprecation 절차

```kotlin
// ✅ Deprecated API 표시
@Deprecated("v2 /api/v2/reviews 사용 권장", ReplaceWith("/api/v2/reviews"))
@GetMapping("/api/reviews")
fun getReviews(): List<ReviewResponse> { ... }
```

```yaml
# OpenAPI에서 deprecated 표시
paths:
  /api/reviews:
    get:
      deprecated: true
      summary: "[Deprecated] 리뷰 목록 조회 — /api/v2/reviews 사용 권장"
```
