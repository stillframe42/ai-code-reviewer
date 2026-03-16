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
- **테스트**: 기능 추가 시 단위 테스트 함께 작성

---

## 언어 규칙

- 응답 언어: 한국어
- 코드 주석: 한국어
- 커밋 메시지: 한국어 제목
- 문서: 한국어
