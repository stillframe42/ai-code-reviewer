package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.port.CostLogEntry
import stillframe42.aicodereviewer.common.port.CostLogPort
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.LlmCostLogEntity

// CostLogPort 구현체 — LlmCostLogRepository에 위임하여 LLM 비용 로그를 저장한다
@Component
class CostLogAdapter(private val repository: LlmCostLogRepository) : CostLogPort {

    override fun save(entry: CostLogEntry) {
        repository.save(
            LlmCostLogEntity(
                modelName = entry.modelName,
                promptTokens = entry.promptTokens,
                completionTokens = entry.completionTokens,
                estimatedCostUsd = entry.estimatedCostUsd,
            )
        )
    }
}
