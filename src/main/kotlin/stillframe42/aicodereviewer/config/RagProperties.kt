package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// RAG 인덱싱 관련 설정 — app.rag.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag")
data class RagProperties(
    val autoIndex: Boolean = true,
    // Exponential Backoff 기본 대기 시간 (각 재시도마다 2배 증가)
    // Spring Boot는 java.time.Duration을 ISO 8601(PT2S) 및 숫자(ms) 형식으로 바인딩함
    val retryBaseDelay: Duration = Duration.ofSeconds(2),
    // 벡터 유사도 검색 임계값 — 이 값 미만의 문서는 RRF 후보에서 제외된다.
    // B-2 sweep 결과(sweep-results/threshold-decision.md): 0.5/0.6/0.7/0.8 측정에서
    // 0.7이 R+P 합 최댓값(0.715, 4차 baseline 0.0 대비 +0.040). Recall 동일, Precision 미세 개선.
    // 0.8은 토큰 평균 9로 폭락(검색 사실상 비활성)이라 채택 불가.
    val similarityThreshold: Double = 0.7,
    // 검색 결과 반환 개수 — RRF 통합 후 상위 topK개 반환.
    // B-1 sweep 결과(sweep-results/topk-decision.md): 3/5/7 측정에서 3이 R+P 합 최댓값(0.775)
    // + 가장 적은 토큰(355). SEC Precision 0.800으로 가장 양호.
    val topK: Int = 3,
    // STYLE 카테고리는 범용 룰이라 카테고리 필터링이 검색 풀을 과도하게 좁힘.
    // 측정/실험 단계에서 활성화하여 효과 검증 후 production 적용 여부 결정 (Phase 4).
    val styleFilterBypass: Boolean = false,
    // Multi-query Retrieval 활성화 — true 시 원본 쿼리를 LLM으로 3가지 변형 후 각각 검색 → 결과 통합 RRF.
    // production default false, 측정/실험 단계에서만 true. Phase 4에서 적용 여부 결정.
    val multiQueryEnabled: Boolean = false,
    // 리뷰 응답의 클레임을 별도 LLM 호출로 사후 검증 (C-1 Method 3).
    // verified만 통과시키고 rejected는 필터링 — Faithfulness 개선 목적.
    // production default false, 측정/실험 단계에서만 true. Step C 종합 후 적용 여부 결정.
    val claimVerifyEnabled: Boolean = false,
)
