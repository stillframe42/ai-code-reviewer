package stillframe42.aicodereviewer.config

import java.math.BigDecimal
import org.springframework.boot.context.properties.ConfigurationProperties

// 모델별 LLM 호출 단가 설정 — application-ai.yml의 app.ai.cost 섹션과 바인딩
@ConfigurationProperties(prefix = "app.ai.cost")
data class LlmCostProperties(
    // 모델명 → 단가 맵 (예: "claude-haiku-4-5-20251001" → ModelCost)
    val models: Map<String, ModelCost> = emptyMap(),
) {
    // 1,000토큰당 단가 (USD)
    data class ModelCost(
        val inputPer1k: BigDecimal = BigDecimal.ZERO,
        val outputPer1k: BigDecimal = BigDecimal.ZERO,
    )
}
