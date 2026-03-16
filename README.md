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

## 라이선스

MIT License
