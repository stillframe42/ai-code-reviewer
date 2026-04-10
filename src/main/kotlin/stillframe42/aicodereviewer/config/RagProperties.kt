package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// TokenTextSplitter 및 RAG 인덱싱 관련 설정 — app.rag.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag")
data class RagProperties(
    val chunkSize: Int = 512,
    val minChunkSizeChars: Int = 100,
    val minChunkLengthToEmbed: Int = 50,
    val maxNumChunks: Int = 10000,
    val keepSeparator: Boolean = true,
    val autoIndex: Boolean = true,
)
