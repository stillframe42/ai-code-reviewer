package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.LlmCostLogEntity
import java.math.BigDecimal

interface LlmCostLogRepository : JpaRepository<LlmCostLogEntity, Long> {

    // 모델명별 비용 합계 — [modelName: String, sumCost: BigDecimal] 쌍의 배열 목록
    @Query("SELECT l.modelName, SUM(l.estimatedCostUsd) FROM LlmCostLogEntity l GROUP BY l.modelName")
    fun sumCostGroupByModel(): List<Array<Any>>

    // 전체 누적 비용 합계 (레코드 없으면 null)
    @Query("SELECT SUM(l.estimatedCostUsd) FROM LlmCostLogEntity l")
    fun sumTotalCost(): BigDecimal?
}
