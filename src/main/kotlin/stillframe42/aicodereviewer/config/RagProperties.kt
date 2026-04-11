package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// RAG 인덱싱 관련 설정 — app.rag.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag")
data class RagProperties(
    val autoIndex: Boolean = true,
    // Exponential Backoff 기본 대기 시간 (각 재시도마다 2배 증가)
    // Spring Boot는 java.time.Duration을 ISO 8601(PT2S) 및 숫자(ms) 형식으로 바인딩함
    val retryBaseDelay: Duration = Duration.ofSeconds(2),
)
