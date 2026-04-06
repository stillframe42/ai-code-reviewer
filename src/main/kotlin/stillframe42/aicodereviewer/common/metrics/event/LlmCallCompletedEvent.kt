package stillframe42.aicodereviewer.common.metrics.event

import java.math.BigDecimal

// LLM 호출 완료 이벤트 — 토큰 사용량과 비용 데이터를 담아 발행
data class LlmCallCompletedEvent(
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val costUsd: BigDecimal,
)
