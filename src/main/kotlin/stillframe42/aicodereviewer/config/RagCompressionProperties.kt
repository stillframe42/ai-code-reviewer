package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// RAG 컨텍스트 압축 관련 설정 — app.rag.compression.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag.compression")
data class RagCompressionProperties(
    // 압축 LLM 모델명 (gpt-4o-mini 권장 — 비용/성능 균형)
    val model: String = "gpt-4o-mini",
    // 청크 토큰 수가 이 값 이하이면 압축을 우회한다 (작은 청크는 단일 토픽 = 노이즈 적음).
    val compressionThresholdTokens: Int = 300,
)
