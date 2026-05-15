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
| v8 | review-system-v8.st | 2026-04-01 | v7 기반 + Tool Calling 사용 지침 추가 (diff 외부 타입 참조 시 도구 호출 명시) |
| v9 | review-system-v9.st | 2026-04-15 | v8 기반 + RAG 컨벤션 컨텍스트 주입 (`{convention_section}`) |
| v10 | review-system-v10.st | 2026-04-14 | v9 기반 + BeanOutputConverter format 스키마 주입 (`{format}`) |
| v11 | review-system-v11.st | 2026-04-24 | v10 기반 + Faithfulness 개선 Method 1: 컨벤션 외 주장 자제 명시 — C-1 채택 |
| v12 (미채택) | review-system-v12.st | 2026-04-24 | v11 기반 + Faithfulness 개선 Method 2: CoT (issue.reasoning) — **C-1 미채택** |
| **v13 (활성)** | review-system-v13.st | 2026-04-24 | v11 기반 + Few-shot 예시 3개 (SEC-002·ARCH-001·STYLE-001) — **C-2 채택, production 적용** |
| v14 (미채택) | review-system-v14.st | 2026-04-22 | v13 기반 + API 카테고리 Few-shot 예시 1개 (API-002 에러 응답 혼재) — **B2-1 미채택, 인프라 보존** |

## 버전 전환 방법

`src/main/resources/application-ai.yml`에서 경로를 변경한다:

```yaml
app:
  prompt:
    review-system: classpath:prompts/review/review-system-v13.st  # v13 (현재 활성, C-2 채택)
    # review-system: classpath:prompts/review/review-system-v11.st  # v11 (C-1 채택, 직전 안정 버전)
    # review-system: classpath:prompts/review/review-system-v1.st   # v1 (초기 버전, 롤백용)
```

## 다음 버전 작성 가이드

새 프롬프트를 만들 때는 **현재 활성(production) 버전을 베이스로 분기**한다.

- **현재 활성: v13** (`review-system-v13.st`) — C-2 Few-shot 채택본 (v11 + 3개 예시)
- v12는 C-1 Method 2 실험으로 만들었으나 채택되지 않음 — **v12를 베이스로 사용 금지** (CoT 단계가 production에 적용되지 않으므로 후속 버전이 v12를 상속하면 의도치 않은 reasoning 강제가 따라옴)
- 향후 v14 등 신규 변형은 v13.st를 베이스로 분기

## 변경 이력

### v14 (2026-04-22) — **B2-1 미채택, 인프라 보존**

- **변경 이유**: v13 이후 API 카테고리 Relevancy 정체 (0.66) — v13 예시가 SEC·ARCH·STYLE 3개만 포함하여 API 카테고리 few-shot 가이드 부재. v13 측정 중 API-002(inconsistent-error-response)가 가장 높은 품질(Recall 1.0, Relevancy 1.0)을 보여 Few-shot 예시 소재로 선정.
- **베이스**: v13 (C-2 채택본)
- **주요 변경**:
  - "예시 4 — API (에러 응답 형식 혼재)" 섹션 추가
  - 입력 발췌: API-002 patch의 3가지 ExceptionHandler 구조 (약 22줄)
  - 좋은 응답: issue 2건 (API 에러 스키마 혼재 + SECURITY stackTrace 노출) — 한 patch에서 복수 카테고리 발견 가능성을 학습시키는 의도
  - 토큰 추가: 시스템 프롬프트 ~300-400 토큰 증가 (매 요청)
- **B2-1 측정 결과** (sweep-results/v14-result.json, v13 대비):
  - Faithfulness 평균: 0.050 → 0.100 (Δ +0.050)
  - Precision 평균: 0.400 → 0.350 (Δ -0.050)
  - Recall 평균: 0.400 → 0.350 (Δ -0.050)
  - Relevancy 평균: 0.770 → 0.775 (Δ +0.005)
  - 카테고리별 Relevancy: SEC 0.920→0.920 (0), ARCH 0.840→0.800 (-0.040), STYLE 0.660→0.800 (+0.140 — B1 code-body-query production 적용 효과로 추정, v14 본질 효과 아님), **API 0.660→0.580 (-0.080 — 목표와 반대 방향)**
  - API 케이스별 Relevancy Δ: API-001 +0.3, API-002 0 (예시 소스), API-003 -0.2, API-004 -0.1, API-005 -0.4
- **결론**: 미채택. API Few-shot 1개 추가가 의도(API Relevancy +)와 반대 효과 (Δ -0.080). API-002 예시가 너무 특정 패턴이라 다른 API 케이스를 "예시 기준과 불일치"로 필터링한 것으로 추정. `review-system-v14.st` 파일은 향후 Few-shot 재실험(다른 API 예시 선정 또는 균형 조정)을 위해 보존. application-ai.yml은 v13 유지.

### v13 (2026-04-24) — **C-2 채택, production 적용**

- **변경 이유**: C-2 Few-shot — Answer Relevancy 추가 개선을 위해 4차 baseline + C-1 측정 결과 중 (Faithfulness + Relevancy)/2 상위 케이스를 카테고리별로 1개씩 선별하여 예시로 추가
- **베이스**: v11 (C-1 채택본)
- **주요 변경**:
  - "[좋은 리뷰 예시]" 섹션 추가 (3개 케이스: SEC-002 평문 비밀번호, ARCH-001 N+1 쿼리, STYLE-001 장함수 SRP)
  - 각 예시는 입력 발췌(5-10줄) + 좋은 응답의 핵심 issue 1건 (description·suggestion 포함)
  - 토큰 추가: 시스템 프롬프트 ~400-500 토큰 증가 (매 요청)
- **C-2 측정 결과** (sweep-results/few-shot-result.json, v11 대비):
  - Faithfulness 0.050 (변화 없음 — 평가 LLM 한계)
  - Precision 0.370 → 0.400 (+0.030)
  - Recall 0.400 (변화 없음)
  - Relevancy 0.710 → 0.770 (+0.060)
  - 카테고리별 Relevancy: SEC +0.040, **ARCH +0.220 (큰 개선)**, STYLE -0.020, API ±0
- **결론**: 채택. application-ai.yml의 `app.prompt.review-system`을 v13으로 전환 (2026-04-24).

### v12 (2026-04-24) — **C-1 미채택**

- **변경 이유**: C-1 Method 2 실험 — CoT로 issue 도출 reasoning을 강제하여 환각 감소 + 추론 투명성 확보
- **주요 변경**:
  - v11을 베이스로 유지
  - "추론 단계 (Chain-of-Thought)" 섹션 추가 — issue.reasoning에 ① 컨벤션 인용 ② 충돌 매칭 ③ 개선 방법 3단계
  - `CodeIssue` 도메인 모델에 옵셔널 `reasoning: String?` 필드 추가 (다른 버전에서는 null)
- **C-1 측정 결과** (sweep-results/c1-method2-result.json):
  - Faithfulness 0.050 (Method 1과 동일), Precision 0.290 (Method 1 대비 -0.080), Relevancy 0.690 (-0.020)
  - reasoning 작성 토큰이 issue 본문 품질을 일부 희석 — Method 1보다 모든 면에서 열등
- **결론**: 미채택. 후속 버전 작성 시 v12를 베이스로 사용 금지 — 항상 현재 활성(v11)에서 분기.
  CodeIssue.reasoning 필드는 옵셔널 null로 호환 보존 (향후 다른 활용 여지)

### v11 (2026-04-24) — **C-1 채택, production 적용**

- **변경 이유**: 베이스라인 측정에서 Faithfulness 0.000 (컨벤션에 근거하지 않은 환각성 주장 다수). 명시적 제약으로 환각률 감소 시도 (C-1 Method 1)
- **주요 변경**:
  - v10을 베이스로 유지
  - 원칙 6번 추가: "[참고 컨벤션 문서]가 제공된 경우, 그 안에서 근거를 찾을 수 없는 주장은 하지 마십시오. 컨벤션이 다루지 않는 영역은 일반 원칙으로 짧게 언급하되 단정적이지 않게 표현합니다."
- **C-1 측정 결과** (sweep-results/c1-method1-result.json, 4차 baseline 대비):
  - Faithfulness 0.000 → 0.050 (+0.050)
  - Precision 0.250 → 0.370 (+0.120)
  - Recall 0.425 → 0.400 (-0.025, 환각 자제로 정답 일부도 차단)
  - Relevancy 0.670 → 0.710 (+0.040)
  - 추가 비용 0 (프롬프트 1줄 추가)
- **결론**: 채택. application-ai.yml의 `app.prompt.review-system`을 v11로 전환 (2026-04-24).

### v10 (2026-04-14)

- **변경 이유**: `buildVariables()`에서 `converter.getFormat()`을 주입하지 않아 AI가 `overall_score` 필드를 인지하지 못해 항상 0으로 반환되는 버그 수정
- **주요 변경**:
  - v9를 베이스로 유지
  - `{format}` 플레이스홀더 추가 — `ReviewAdapter`가 `BeanOutputConverter.getFormat()`을 주입하여 JSON 스키마 명세를 프롬프트에 포함

### v9 (2026-04-15)

- **변경 이유**: RAG 기반 컨벤션 컨텍스트 통합 — 리뷰 대상 파일의 카테고리(SECURITY/API/ARCH/STYLE)에 맞는 컨벤션 문서를 검색해 프롬프트에 주입하여 컨벤션 기반 피드백 품질을 높임
- **주요 변경**:
  - v8을 베이스로 유지
  - 첫 줄 페르소나 변경: "AI Code Reviewer" → "시니어 백엔드 엔지니어"
  - `{convention_section}` 플레이스홀더 추가 — `ReviewAdapter`가 RAG 검색 결과를 "[참고 컨벤션 문서]\n{내용}" 형태로 주입. 검색 결과 없으면 빈 문자열로 치환되어 기존 동작 유지

### v8 (2026-04-01)

- **변경 이유**: WITH_TOOLS 모드에서 Tool 호출 횟수가 0회로 측정됨 — 시스템 프롬프트에 Tool 사용 지침이 없어 AI가 diff만으로 답변하는 기본 전략을 유지했기 때문
- **주요 변경**:
  - v7을 베이스로 유지
  - "도구 사용 지침" 섹션 추가
  - 호출해야 하는 상황 명시: diff에 없는 타입·인터페이스 정의, 메서드 시그니처 미확인, PR 의도 불명확, 파일 변경 이력 맥락 필요
  - 호출하지 않아도 되는 상황 명시: 표준 라이브러리(`java.*`, `kotlin.*`, `springframework.*`) 사용 시 — 불필요한 Tool 호출 방지

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
