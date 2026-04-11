package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// RAG 인덱싱 관련 설정 — app.rag.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag")
data class RagProperties(
    val autoIndex: Boolean = true,
    // Exponential Backoff 기본 대기 시간 (각 재시도마다 2배 증가)
    val retryBaseDelay: Duration = 2.seconds,
)
