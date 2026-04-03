package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Langfuse LLM Observability 설정을 바인딩하는 프로퍼티 클래스
// secretKey → secret-key, publicKey → public-key (Spring Boot kebab-case 자동 변환)
@ConfigurationProperties(prefix = "langfuse")
data class LangfuseProperties(
    val host: String = "http://localhost:3000",
    val secretKey: String = "",
    val publicKey: String = "",
)
