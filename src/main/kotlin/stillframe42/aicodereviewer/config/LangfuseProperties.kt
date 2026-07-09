package stillframe42.aicodereviewer.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

// Langfuse LLM Observability 설정을 바인딩하는 프로퍼티 클래스
// secretKey → secret-key, publicKey → public-key (Spring Boot kebab-case 자동 변환)
@ConfigurationProperties(prefix = "langfuse")
data class LangfuseProperties(
    val host: String = "http://localhost:3000",
    val secretKey: String = "",
    val publicKey: String = "",
    // ingestion 요청 전체(연결+응답) 상한 — LLM 호출 스레드에서 block 하므로 무한 대기 금지
    val ingestTimeout: Duration = Duration.ofSeconds(3),
)
