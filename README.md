# ai-code-reviewer

> Spring Boot 4.0.3 + Kotlin + Spring AI 기반의 AI 코드 리뷰 자동화 시스템

---

## 시스템 개요

`ai-code-reviewer`는 코드 변경사항을 자동으로 분석하고 품질 피드백을 제공하는 AI 코드 리뷰 시스템입니다.

### 핵심 기능

- **자동 코드 리뷰**: Pull Request 또는 코드 스니펫을 AI가 분석하여 개선 사항 제안
- **다양한 리뷰 관점**: 코드 품질, 보안 취약점, 성능, 가독성 등 다각도 분석
- **Spring AI 통합**: OpenAI / Anthropic Claude 등 다양한 LLM 백엔드 지원
- **REST API 제공**: 외부 시스템(GitHub Actions, GitLab CI 등)과 손쉬운 연동

### 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Kotlin |
| 프레임워크 | Spring Boot 4.0.3 |
| AI 통합 | Spring AI |
| 빌드 도구 | Gradle (Kotlin DSL) |
| JDK | JDK 25 |

---

## 아키텍처

<!-- TODO: 아키텍처 다이어그램 추가 -->
<!--
  아래 Mermaid 다이어그램 블록으로 교체 예정:

  ```mermaid
  graph TD
      Client["클라이언트 (GitHub Actions / API 호출)"]
      API["REST API Layer (Spring MVC / WebFlux)"]
      Service["CodeReview Service"]
      SpringAI["Spring AI (ChatClient)"]
      LLM["LLM Backend (OpenAI / Claude)"]
      DB["결과 저장소 (DB / Cache)"]

      Client --> API --> Service --> SpringAI --> LLM
      Service --> DB
  ```
-->

```
[ 클라이언트 ]
     │  REST API 요청
     ▼
[ API Layer ]          ← Spring MVC / WebFlux
     │
     ▼
[ CodeReview Service ] ← 리뷰 로직, 프롬프트 구성
     │
     ▼
[ Spring AI ChatClient ] ← 모델 추상화 레이어
     │
     ▼
[ LLM Backend ]        ← OpenAI GPT / Anthropic Claude 등
```

---

## 로컬 실행 방법

### 사전 요구사항

- JDK 25
- Gradle 8.x (또는 `./gradlew` Wrapper 사용)
- LLM API 키 (OpenAI 또는 Anthropic)

### 1. 저장소 클론

```bash
git clone https://github.com/your-org/ai-code-reviewer.git
cd ai-code-reviewer
```

### 2. 환경 변수 설정

`src/main/resources/application-local.yml` 파일을 생성하고 API 키를 설정합니다.

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
    # anthropic:
    #   api-key: ${ANTHROPIC_API_KEY}
```

또는 환경 변수로 직접 전달할 수 있습니다.

```bash
export OPENAI_API_KEY=sk-...
```

### 3. 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 4. 동작 확인

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health

# 코드 리뷰 요청 예시
curl -X POST http://localhost:8080/api/v1/review \
  -H "Content-Type: application/json" \
  -d '{
    "language": "kotlin",
    "code": "fun add(a: Int, b: Int) = a + b"
  }'
```

---

## 프로젝트 구조

```
src/
└── main/
    └── kotlin/
        └── com/example/aicodereview/
            ├── api/          # REST 컨트롤러
            ├── service/      # 리뷰 비즈니스 로직
            ├── prompt/       # 프롬프트 템플릿 관리
            └── config/       # Spring AI 설정
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

v1 / v2 / v3 시스템 프롬프트 버전 간 이슈 감지 품질을 비교하는 벤치마크입니다.
7개 픽스처 × 3개 버전 = 총 21회 AI 호출이 발생합니다.

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
시스템 프롬프트 토큰 추정: v1=35 / v2=290 / v3=240

버전 | 픽스처                                 | 점수 | 이슈수 | CRIT | SEC | PERF | READ | ARCH | 응답시간 | 필드완전
--------------------------------------------------------------------
v1  | security-sql-injection                 |  4  |    1  |   0 |   1 |    0 |    0 |    0 |  1200ms | O
v2  | security-sql-injection                 |  3  |    2  |   1 |   2 |    0 |    0 |    0 |  1400ms | O
v3  | security-sql-injection                 |  3  |    3  |   2 |   3 |    0 |    0 |    0 |  1300ms | O
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
