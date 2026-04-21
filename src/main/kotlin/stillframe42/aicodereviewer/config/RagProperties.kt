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
    // RRF 혼합 검색에서는 후보를 충분히 넓게 수집해야 하므로 0.0(필터링 없음)을 기본값으로 사용한다.
    // 순수 벡터 검색 단독 사용 시에는 호출 지점에서 명시적으로 0.7 등을 전달한다.
    val similarityThreshold: Double = 0.0,
    // STYLE 카테고리는 범용 룰이라 카테고리 필터링이 검색 풀을 과도하게 좁힘.
    // 측정/실험 단계에서 활성화하여 효과 검증 후 production 적용 여부 결정 (Phase 4).
    val styleFilterBypass: Boolean = false,
)
