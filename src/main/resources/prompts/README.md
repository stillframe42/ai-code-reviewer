# 프롬프트 버전 이력

이 디렉토리의 시스템 프롬프트는 버전별로 관리됩니다.
활성 버전은 `application-ai.yml`의 `app.prompt.review-system` 설정으로 제어합니다.

## 버전 목록

| 버전 | 파일 | 날짜 | 요약 |
|------|------|------|------|
| v1 | review-system-v1.st | 2025 | 초기 버전: 단순 역할 정의 9줄 |
| v2 | review-system-v2.st | 2026-03-19 | 카테고리 4종, 심각도 4단계, few-shot 2쌍 추가 |
| v3 | review-system-v3.st | 2026-03-19 | 토큰 최적화: 메타 설명·중복 규칙 제거, ~50-60 토큰 절감 |
| v4 | review-system-v4.st | 2026-03-21 | v1 기반 재정립: few-shot·카테고리·심각도 정의 제거, 필수 스키마 규칙만 유지 |
| v5 | review-system-v5.st | 2026-03-24 | v1 기반 + 파일 유형별 지침: [QUERY_REVIEW] 헤더 파일에 대한 리뷰 방식 안내 추가 |
| v6 | review-system-v6.st | 2026-03-25 | v5 기반 + filename 필드 안내 추가 (diff position 매핑을 위한 파일 경로 반환 요청) |
| v7 | review-system-v7.st | 2026-03-25 | v6 기반 + summary 2문장 제한, positives 최대 3개 제한 (가독성 개선) |

## 버전 전환 방법

`src/main/resources/application-ai.yml`에서 경로를 변경한다:

```yaml
app:
  prompt:
    review-system: classpath:prompts/review-system-v1.st  # v1으로 전환
    # review-system: classpath:prompts/review-system-v2.st  # v2
    # review-system: classpath:prompts/review-system-v3.st  # v3 (기본)
```

## 변경 이력

### v7 (2026-03-25)

- **변경 이유**: PR summary 코멘트가 너무 길고 개별 이슈와 내용이 중복됨. Positives 항목도 과다하여 가독성이 떨어짐
- **주요 변경**:
  - v6을 베이스로 유지
  - `summary`: 2문장 이내 총평, 개별 이슈 내용 반복 금지
  - `positives`: 최대 3개만 반환

### v6 (2026-03-25)

- **변경 이유**: Phase 3 인라인 라인 코멘트 구현 — `DiffPositionResolver`가 `(filename, line)` 쌍을 이용해 diff position을 계산하려면 AI가 이슈 발생 파일 경로를 반환해야 함
- **주요 변경**:
  - v5를 베이스로 유지
  - "이슈 위치 정보" 섹션 3줄 추가 (~30토큰 증가)
  - `filename` 필드: diff에서 확인 가능한 파일 경로 기입, 특정 불가 시 null
  - `line` 필드: 새 파일 기준 라인 번호 명시 (기존 안내 강화)

### v5 (2026-03-24)

- **변경 이유**: 파일 확장자별 처리 전략 구현으로 `DiffPreprocessor`가 설정/쿼리 파일에 `[QUERY_REVIEW]` 헤더 주석을 삽입하게 됨. AI가 이 마커를 인식하고 적절한 리뷰 방식을 적용하도록 지침 추가
- **주요 변경**:
  - v1을 베이스로 유지 (9줄 원칙 그대로)
  - "파일 유형별 리뷰 지침" 섹션 2줄 추가 (~25토큰 증가)
  - `[QUERY_REVIEW]` 헤더가 붙은 파일에 대해 변경 의도·설정값 적절성·구조적 영향 위주 리뷰 지침 명시

### v4 (2026-03-21)

- **변경 이유**: 벤치마크 결과 v1(61토큰)이 v2/v3(470-523토큰)보다 품질 점수 우위 — few-shot·카테고리·심각도 정의가 모델 기존 지식과 중복되어 토큰 낭비임을 확인
- **주요 변경**:
  - v1을 베이스로 재정립
  - few-shot 예시 2쌍 제거 (약 250토큰 절감)
  - 카테고리 4종·심각도 4단계 정의 제거 (모델 기본 지식으로 충분)
  - Senior Backend Engineer 페르소나 제거
  - 모델이 추론 불가한 항목만 유지: `id` 형식(`"ISSUE-1"`), `line` null 처리

### v3 (2026-03-19)

- **변경 이유**: v2 프롬프트의 메타 설명·중복 표현이 불필요한 토큰을 소비
- **주요 변경**:
  - 예시 레이블("예시 1 — ...", "입력 코드:", "출력:") 및 안내 문장 제거
  - 출력 규칙 2줄 → 1줄 통합 ("issues, positives에 해당 없으면 각각 빈 배열([]) 반환")
  - "JSON 외 어떤 텍스트도 출력하지 않는다" → "JSON만 출력" 으로 단축
  - few-shot 예시 본문은 품질 유지를 위해 그대로 보존
  - 예상 절감: ~50-60 토큰/요청

### v2 (2026-03-19)

- **변경 이유**: 단순 역할 정의만으로는 AI 응답 형식이 불일치하는 경우가 발생
- **주요 변경**:
  - Senior Backend Engineer 페르소나 부여로 리뷰 관점 구체화
  - 이슈 카테고리 4종 정의 (PERFORMANCE / SECURITY / READABILITY / ARCHITECTURE)
  - 심각도 4단계 정의 (CRITICAL / MAJOR / MINOR / SUGGESTION)
  - 출력 규칙 명세화 (JSON only, id 형식, null 처리 등)
  - few-shot 예시 2쌍 추가로 응답 형식 일관성 향상

### v1 (2025)

- **초기 버전**: 역할 정의 및 기본 원칙 5가지
