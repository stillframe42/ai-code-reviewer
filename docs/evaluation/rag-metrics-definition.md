# RAG 품질 평가 지표 정의서

> 작성일: 2026-04-20 (DAY 15)
> 목적: RAG 파이프라인의 품질을 정량적으로 측정하고, 데이터 기반 개선 루프를 구동하기 위한 지표 체계

---

## 1. 평가 대상 파이프라인

```
리뷰 대상 파일 (diff)
  → FileCategoryMapper.selectCategory()        ... 카테고리 결정
  → HybridConventionSearchService.search()     ... 하이브리드 검색
  │   ├─ ConventionVectorPort.search()         ... 벡터 검색 (candidateSize = topK × 2)
  │   ├─ ConventionKeywordSearchPort.search()  ... 키워드 검색 (candidateSize = topK × 2)
  │   └─ reciprocalRankFusion()                ... RRF 병합 (k=60, 상위 topK개)
  → ContextCompressorPort.compress()           ... 컨텍스트 압축 (ARCH 카테고리 우회)
  → ReviewAdapter.reviewCode()         ... LLM 리뷰 생성 (conventionContext 주입)
  → CodeReview                                 ... 최종 리뷰 결과
```

### 현재 설정값 (베이스라인 기준)

| 설정 | 값 | 출처 |
|------|-----|------|
| `topK` | 5 | `ConventionContextService.buildContext()` |
| `candidateSize` | 10 (topK × 2) | `HybridConventionSearchService.searchRaw()` |
| `similarity-threshold` | 0.0 | `application-ai.yml` |
| `RRF k` | 60 | `ReciprocaRankFusion.kt` |
| 압축 모델 | gpt-4o-mini | `application-ai.yml` |
| 압축 임계값 | 300 토큰 | `application-ai.yml` |
| ARCH 카테고리 | 압축 우회 | `HybridConventionSearchService.search()` |

### 컨벤션 문서 (검색 대상)

| 파일 | 카테고리 |
|------|----------|
| `conventions/kotlin-style.md` | STYLE |
| `conventions/architecture-guide.md` | ARCH |
| `conventions/api-design.md` | API |
| `conventions/security-checklist.md` | SECURITY |

---

## 2. 평가 지표 정의

### 2.1 Context Precision (검색 정밀도)

| 항목 | 내용 |
|------|------|
| **측정 대상** | Retrieval 단계 (`HybridConventionSearchService`) |
| **정의** | 검색된 청크 중 코드 문제와 실제 관련 있는 청크의 비율 |
| **산출 공식** | `Precision = 관련 있는 청크 수 / 검색된 전체 청크 수` |
| **평가 방식** | LLM-as-a-Judge: 각 검색 청크와 입력 코드/쿼리를 LLM에 제시하고 관련성 판정 |
| **점수 범위** | 0.0 ~ 1.0 (1.0 = 모든 청크가 관련 있음) |
| **목표** | **≥ 0.80** |

**측정 의미**: Precision이 낮으면 관련 없는 청크가 LLM 컨텍스트 윈도우를 낭비하고, 노이즈로 인해 리뷰 품질이 저하된다. 현재 `similarity-threshold=0.0`으로 필터링 없이 RRF 순위만 사용하므로, 관련 없는 청크가 혼입될 가능성이 있다.

**개선 레버**:
- Similarity Threshold 상향 (0.5 ~ 0.8)
- 카테고리 필터링 강화
- RRF k값 조정

---

### 2.2 Context Recall (검색 재현율)

| 항목 | 내용 |
|------|------|
| **측정 대상** | Retrieval 단계 (`HybridConventionSearchService`) |
| **정의** | 코드 문제에 관련된 컨벤션 청크 중 실제로 검색된 비율 |
| **산출 공식** | `Recall = 검색된 관련 청크 수 / 관련 있어야 할 전체 청크 수` |
| **평가 방식** | LLM-as-a-Judge: 골든 데이터셋의 `expected_issues` + `relevant_convention`을 기준으로, 검색 결과가 해당 이슈를 커버하는지 판정 |
| **점수 범위** | 0.0 ~ 1.0 (1.0 = 관련 청크를 모두 검색함) |
| **목표** | **≥ 0.75** |

**측정 의미**: Recall이 낮으면 LLM이 컨벤션 근거 없이 리뷰를 생성하게 되어 Faithfulness와 Answer Relevancy 모두 저하된다. 현재 `topK=5`로 고정이라, 복수 카테고리에 걸친 위반이 있는 코드에서 일부 관련 청크가 누락될 수 있다.

**개선 레버**:
- TopK 증가 (5 → 7)
- Multi-query Retrieval (쿼리를 여러 표현으로 변환 후 합산)
- Similarity Threshold 하향 (Precision과 트레이드오프)

---

### 2.3 Faithfulness (충실도)

| 항목 | 내용 |
|------|------|
| **측정 대상** | Generation 단계 (`ReviewAdapter`) |
| **정의** | 생성된 리뷰의 모든 주장(claim)이 검색된 컨텍스트에 근거하는 비율 |
| **산출 공식** | `Faithfulness = 컨텍스트에 근거한 클레임 수 / 리뷰의 전체 클레임 수` |
| **평가 방식** | LLM-as-a-Judge: 리뷰의 각 주장을 추출 → 각 주장이 검색 컨텍스트에서 뒷받침되는지 판정 |
| **점수 범위** | 0.0 ~ 1.0 (1.0 = 모든 주장이 컨텍스트에 근거함) |
| **목표** | **≥ 0.90** |

**측정 의미**: Faithfulness가 낮으면 LLM이 컨벤션 문서에 없는 규칙을 지어내는 **환각(hallucination)**이 발생한다. 예: "프로젝트 컨벤션에서 함수는 30줄 이하여야 합니다"라고 했지만, 실제 컨벤션 문서에 30줄 규칙이 없는 경우. 이는 리뷰 시스템에 대한 개발자 신뢰를 직접적으로 훼손하므로, **4가지 지표 중 목표값이 가장 높다**.

**개선 레버**:
- 프롬프트 명시적 제약 ("컨텍스트에 없는 내용은 언급하지 마세요")
- Chain-of-Thought 적용 (인용 → 분석 → 제안 순서 강제)
- 응답 후처리: 생성된 클레임을 컨텍스트와 자동 대조

---

### 2.4 Answer Relevancy (응답 관련성)

| 항목 | 내용 |
|------|------|
| **측정 대상** | Generation 단계 (`ReviewAdapter`) |
| **정의** | 생성된 리뷰가 코드의 실제 문제점과 관련 있는 비율 |
| **산출 공식** | `Relevancy = 코드 문제와 관련 있는 코멘트 수 / 전체 코멘트 수` |
| **평가 방식** | LLM-as-a-Judge: 입력 코드의 문제점과 생성된 리뷰를 LLM에 제시 → 각 코멘트의 관련성 판정 |
| **점수 범위** | 0.0 ~ 1.0 (1.0 = 모든 코멘트가 관련 있음) |
| **목표** | **≥ 0.85** |

**측정 의미**: Relevancy가 낮으면 리뷰에 노이즈성 코멘트가 포함된다. 예: SQL Injection 취약점이 있는 코드에 대해 "변수명을 더 명확하게 해주세요" 같은 비핵심 코멘트가 주를 이루는 경우. 2주차 비교 실험에서 RAG OFF 시 노이즈 코멘트 2건, RAG ON 시 1건이었던 것을 더 개선한다.

**개선 레버**:
- Few-shot 예시 (좋은 리뷰 샘플을 프롬프트에 포함)
- 리뷰 프롬프트의 우선순위 지시 강화 (CRITICAL → MAJOR → MINOR 순서)

---

### 2.5 End-to-End Hit Rate (종합 검출률)

| 항목 | 내용 |
|------|------|
| **측정 대상** | 전체 파이프라인 (검색 → 생성) |
| **정의** | 골든 데이터셋의 `expected_issues` 중 최종 리뷰에서 실제로 검출된 이슈 비율 |
| **산출 공식** | `Hit Rate = 검출된 이슈 수 / expected_issues 총 수` |
| **평가 방식** | LLM-as-a-Judge + 수동 보정: 기대 이슈 목록과 리뷰 결과를 대조하여 매칭 |
| **점수 범위** | 0.0 ~ 1.0 (1.0 = 모든 기대 이슈를 검출함) |
| **목표** | 참고 지표 (별도 임계값 없음 — 다른 4개 지표의 종합 결과) |

**측정 의미**: 최종적으로 "컨벤션 위반을 잡아냈는가"를 직접 측정한다. 다른 4개 지표가 간접 지표라면, Hit Rate는 비즈니스 목표와 직접 연결된다. 다만 자동화 정확도에 한계가 있어 참고 지표로 운용한다.

---

## 3. 평가 체계

### 3.1 평가 모델

| 용도 | 모델 | 이유 |
|------|------|------|
| 리뷰 생성 (평가 대상) | Claude Sonnet / GPT-4o | 프로덕션 모델 |
| 평가 판정 (Judge) | gpt-4o-mini | 비용 절감 (20개 × 4지표 = 80회 호출) |

### 3.2 평가 데이터셋

- **골든 데이터셋**: 20개 테스트 케이스 (카테고리별 5개)
- **저장 위치**: `src/test/resources/fixtures/evaluation/golden-dataset.json`
- **구성**:

| 카테고리 | 케이스 ID | 수량 |
|----------|-----------|------|
| SECURITY | SEC-001 ~ SEC-005 | 5 |
| ARCH | ARCH-001 ~ ARCH-005 | 5 |
| STYLE | STYLE-001 ~ STYLE-005 | 5 |
| API | API-001 ~ API-005 | 5 |

### 3.3 측정 시점

| 시점 | 목적 | 실행 방법 |
|------|------|----------|
| 베이스라인 (DAY 16) | 현재 상태 기록 | 골든 데이터셋 전체 평가 |
| 개선 후 (DAY 18~19) | 각 개선 조치의 효과 측정 | 동일 데이터셋 재평가 |
| 최종 (DAY 20) | 목표 달성 확인 | 동일 데이터셋 최종 평가 |
| 운영 (배포 후) | 실시간 품질 모니터링 | 샘플링 평가 (10% 등) |

---

## 4. 목표 점수 요약

| 지표 | 목표 | 우선순위 | 사유 |
|------|------|---------|------|
| Faithfulness | ≥ 0.90 | 1 (최우선) | 환각은 시스템 신뢰를 직접 훼손 |
| Answer Relevancy | ≥ 0.85 | 2 | 노이즈 코멘트는 개발자 피로도 증가 |
| Context Precision | ≥ 0.80 | 3 | 불필요한 청크는 토큰 낭비 + 간접적 품질 저하 |
| Context Recall | ≥ 0.75 | 4 | 누락 청크는 검출 실패로 이어지지만, 일부 누락은 허용 |
| E2E Hit Rate | 참고 | — | 다른 4개 지표의 종합 결과로 간접 추적 |

### 우선순위 근거

Faithfulness > Relevancy > Precision > Recall 순서인 이유:
1. **환각은 취소 불가** — 잘못된 컨벤션 규칙을 인용하면 개발자가 틀린 방향으로 코드를 수정할 수 있다
2. **노이즈는 무시 가능하지만 피로 누적** — 무관한 코멘트가 많으면 유용한 코멘트도 무시하게 된다
3. **불필요한 청크는 비용 문제** — 토큰 낭비지만 리뷰 결과에 직접 영향은 제한적
4. **청크 누락은 부분적 허용** — 핵심 컨벤션을 놓치면 아쉽지만, 일반 지식으로 보완 가능한 경우도 있다

---

## 5. 실패 패턴 분류 기준 (DAY 17 분석용)

평가 점수가 낮은 케이스를 아래 4가지 유형으로 분류하여 개선 대상을 특정한다.

| 유형 | 이름 | 증상 | 영향 지표 | 개선 방향 |
|------|------|------|----------|----------|
| A | 검색 실패 | 관련 문서가 벡터 스토어에 없음 | Recall ↓ | 컨벤션 문서 보강 |
| B | 청킹 실패 | 관련 내용이 청크 경계에서 잘림 | Recall ↓, Precision ↓ | 청킹 전략 수정 |
| C | 프롬프트 실패 | 컨텍스트가 있지만 LLM이 무시 | Relevancy ↓ | 프롬프트 개선 |
| D | 환각 | 컨텍스트 없이 규칙을 지어냄 | Faithfulness ↓ | 환각 방지 강화 |
