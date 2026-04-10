package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// RAG 인덱싱 관련 설정 — app.rag.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag")
data class RagProperties(
    val autoIndex: Boolean = true,
)
