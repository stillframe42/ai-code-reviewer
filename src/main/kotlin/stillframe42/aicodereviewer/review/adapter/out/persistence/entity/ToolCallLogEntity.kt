package stillframe42.aicodereviewer.review.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// Tool 호출 이력 JPA Entity — tool_call_logs 테이블에 매핑
@Entity
@Table(name = "tool_call_logs")
class ToolCallLogEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "review_request_id", nullable = false)
    val reviewRequestId: Long,

    @Column(name = "tool_name", nullable = false, length = 100)
    val toolName: String,

    @Column(name = "arguments_json", columnDefinition = "TEXT")
    val argumentsJson: String? = null,

    @Column(name = "response_size")
    val responseSize: Int? = null,

    @Column(name = "elapsed_ms")
    val elapsedMs: Int? = null,

    @Column(name = "success", nullable = false)
    val success: Boolean,

    @Column(name = "called_at", nullable = false)
    val calledAt: Instant = Instant.now(),
)
