package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.LlmCostLogEntity

interface LlmCostLogRepository : JpaRepository<LlmCostLogEntity, Long> {

    // 청크 단위 전체 조회 — 집계는 Adapter에서 수행
    fun findAllBy(pageable: Pageable): Page<LlmCostLogEntity>
}
