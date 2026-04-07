package stillframe42.aicodereviewer.common.port

import java.math.BigDecimal

// LLM 호출 비용 저장 아웃바운드 포트 — common 레이어가 feature-specific 구현에 의존하지 않도록 추상화
interface CostLogPort {
    fun save(entry: CostLogEntry)
}

// CostLogPort 저장 단위 — DB Entity와 분리된 도메인 데이터
data class CostLogEntry(
    val modelName: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val estimatedCostUsd: BigDecimal,
)
