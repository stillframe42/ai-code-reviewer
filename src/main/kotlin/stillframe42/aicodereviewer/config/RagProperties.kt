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
    val similarityThreshold: Double = 0.7,
    // 검색 결과 반환 개수 — RRF 통합 후 상위 topK개 반환.
    val topK: Int = 3,
    // STYLE 카테고리는 범용 룰이라 카테고리 필터링이 검색 풀을 과도하게 좁힘 — 측정 시 우회 토글.
    val styleFilterBypass: Boolean = false,
    // Multi-query Retrieval — 원본 쿼리를 LLM으로 3가지 변형 후 각각 검색 → 결과 통합 RRF.
    val multiQueryEnabled: Boolean = false,
    // 리뷰 응답의 클레임을 별도 LLM 호출로 사후 검증 (verified만 통과, rejected는 필터링).
    val claimVerifyEnabled: Boolean = false,
)
