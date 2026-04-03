package stillframe42.aicodereviewer.review.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// LLM 호출 비용 JPA Entity — llm_cost_logs 테이블에 매핑
@Entity
@Table(name = "llm_cost_logs")
class LlmCostLogEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // nullable: 현재는 항상 null, 추후 advisor context를 통해 연결 예정
    @Column(name = "review_request_id")
    val reviewRequestId: Long? = null,

    @Column(name = "model_name", nullable = false, length = 100)
    val modelName: String,

    @Column(name = "prompt_tokens", nullable = false)
    val promptTokens: Int,

    @Column(name = "completion_tokens", nullable = false)
    val completionTokens: Int,

    @Column(name = "estimated_cost_usd", nullable = false, precision = 10, scale = 6)
    val estimatedCostUsd: BigDecimal,

    @Column(name = "called_at", nullable = false)
    val calledAt: Instant = Instant.now(),
)
