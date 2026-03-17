# AI Code Reviewer - 프로젝트 가이드

## 프로젝트 개요

AI 기반 코드 리뷰 자동화 시스템. GitHub PR에 대해 Spring AI를 활용하여 자동으로 코드 리뷰를 생성하고 코멘트를 작성합니다.

**기술 스택**
- Language: Kotlin
- Framework: Spring Boot 4.0.3
- AI: Spring AI
- Build: Gradle Kotlin DSL
- JDK: 25

---

## Git 커밋 규칙

[Conventional Commits](https://www.conventionalcommits.org/) 기반, 한국어 제목 사용.

### 형식

```
타입(범위): 한국어 제목

[본문 - 선택사항]

[푸터 - 선택사항]
```

### 타입 목록

| 타입 | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `style` | 코드 포맷팅, 세미콜론 누락 등 (로직 변경 없음) |
| `refactor` | 버그 수정이나 기능 추가 없는 코드 리팩토링 |
| `test` | 테스트 추가 또는 수정 |
| `chore` | 빌드 프로세스, 패키지 매니저 설정 등 |
| `perf` | 성능 개선 |
| `ci` | CI/CD 파이프라인 변경 |

### 범위 예시

- `review`: 코드 리뷰 기능
- `github`: GitHub API 연동
- `ai`: Spring AI 관련
- `config`: 설정 관련
- `api`: REST API 엔드포인트

### 커밋 예시

```
feat(review): PR 변경사항 자동 분석 기능 추가

fix(github): GitHub Webhook 서명 검증 오류 수정

refactor(ai): 프롬프트 템플릿 구조 개선

chore: Gradle 의존성 버전 업데이트
```

### 커밋 Author 규칙

커밋 시 author 정보는 항상 `git config user.name` / `git config user.email` 값을 사용한다.
`Co-Authored-By` 등 별도 author를 추가하지 않는다.

---

## 브랜치 전략

| 브랜치 | 용도 |
|--------|------|
| `main` | 배포 가능한 안정 브랜치 |
| `feat/*` | 새로운 기능 개발 |
| `fix/*` | 버그 수정 |
| `refactor/*` | 리팩토링 |
| `chore/*` | 설정, 의존성 등 |

---

## 코드 규칙

- **스타일**: [Kotlin 공식 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html) 준수
- **주석**: 한국어로 작성
- **변수명/함수명**: 영어 (카멜케이스)
- **클래스명**: 영어 (파스칼케이스)

### Service 레이어 구조

- Service 클래스는 반드시 **인터페이스와 구현 클래스로 분리**한다.
- 구현 클래스 이름은 인터페이스 이름 앞에 `Default`를 붙인다.
  - 예: `ChatService` (인터페이스) → `DefaultChatService` (구현 클래스)
- Controller는 인터페이스 타입으로 의존한다 (`@Autowired ChatService chatService`).

---

## 패키지 구조

기능(feature) 단위로 패키지를 구성한다.

```
stillframe42.aicodereviewer/
├── config/          # 전역 빈 설정 (@Configuration)
├── common/          # 공통 컴포넌트 (예외 핸들러 등)
└── {기능}/          # 기능별 패키지 (예: chat, review, github)
    ├── {기능}Controller.kt
    ├── {기능}Service.kt         # Service 인터페이스
    ├── Default{기능}Service.kt  # Service 구현 클래스
    └── dto/
        ├── {기능}Request.kt
        └── {기능}Response.kt
```

---

## OpenAPI 명세 관례

- API 명세는 프로젝트 루트의 `openapi.yml` 파일에 **OpenAPI 3.1.0** 형식으로 유지한다.
- **새 API를 추가하거나 기존 API를 변경할 때는 반드시 `openapi.yml`도 함께 업데이트한다.**
- 스키마는 `components/schemas`에 별도로 정의하고 `$ref`로 참조한다 (인라인 작성 금지).
- 에러 응답 스키마는 구조가 다를 경우 별도 스키마로 분리한다.
  - 필드별 유효성 오류: `ValidationErrorResponse` (Map 구조)
  - 단일 메시지 오류: `ErrorResponse` (`error` 필드)
- 각 엔드포인트에는 `operationId`, `summary`, `description`, `tags`를 반드시 작성한다.
- 가능한 모든 HTTP 응답 코드(`200`, `400`, `500` 등)에 대한 응답 스키마와 예시(`example`)를 포함한다.

---

## 테스트 관례

- **코드를 생성할 때는 반드시 테스트 코드를 함께 작성한다.**
- DTO 등 순수 로직 테스트: Spring 컨텍스트 없이 직접 단위 테스트
- **Service 레이어 이상(Service, Controller)**: `@SpringBootTest` 통합 테스트로 작성한다. 통합 테스트 작성 시에 Mock 테스트 금지
- Controller 통합 테스트는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort` + `RestTestClient.bindToServer().baseUrl(...).build()` 사용
  - `RestTestClient` 패키지: `org.springframework.test.web.servlet.client.RestTestClient`
  - `@LocalServerPort` 패키지: `org.springframework.boot.test.web.server.LocalServerPort`
  - 요청 바디는 `.body(value)` 사용 (`bodyValue()` 없음)
  - `TestRestTemplate`, `MockMvc`/`@AutoConfigureMockMvc`, `@Autowired RestTestClient` 자동 주입은 Spring Boot 4에서 사용 불가
- 외부 API 실호출이 필요한 테스트는 `assumeTrue`로 실제 키 존재 여부를 확인해 더미 키 환경에서는 자동 스킵한다

---

## 언어 규칙

- 응답 언어: 한국어
- 코드 주석: 한국어
- 커밋 메시지: 한국어 제목
- 문서: 한국어
