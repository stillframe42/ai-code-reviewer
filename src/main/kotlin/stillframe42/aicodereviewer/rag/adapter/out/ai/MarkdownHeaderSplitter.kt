package stillframe42.aicodereviewer.rag.adapter.out.ai

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import stillframe42.aicodereviewer.rag.domain.port.out.DocumentPreparerPort
import java.util.UUID

// 마크다운 문서를 ## / ### 헤더 기준으로 분리하여 RagDocument 리스트를 반환한다.
// 코드 블록(```) 내 ## 는 헤더로 인식하지 않는다.
// 헤더 이전 intro 내용 및 빈 섹션은 RagDocument를 생성하지 않는다.
@Component
class MarkdownHeaderSplitter : DocumentPreparerPort {

    // 모든 컨벤션 파일을 헤더 기준으로 분리하여 RagDocument 리스트 반환
    override fun prepare(): List<RagDocument> =
        conventionFiles.flatMap { (path, category) ->
            val fileName = path.substringAfterLast("/")
            val text = ClassPathResource(path).inputStream.use { it.bufferedReader().readText() }
            split(
                text,
                mapOf("source" to fileName, "category" to category, "version" to "1.0", "language" to "kotlin"),
            )
        }

    // 단일 텍스트를 ## / ### 헤더 기준으로 분리한다. (단위 테스트 가능하도록 internal 노출)
    internal fun split(text: String, baseMetadata: Map<String, Any>): List<RagDocument> {
        val documents = mutableListOf<RagDocument>()
        var currentH2: String? = null
        var currentH3: String? = null
        var inCodeBlock = false
        val buffer = mutableListOf<String>()

        for (line in text.lines()) {
            // 코드 블록 진입/탈출 추적 (``` 으로 시작하는 줄)
            if (line.trimStart().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                buffer += line
                continue
            }
            if (inCodeBlock) {
                buffer += line
                continue
            }
            when {
                line.startsWith("## ") -> {
                    documents.flushSection(buffer, currentH2, currentH3, baseMetadata)
                    currentH2 = line.removePrefix("## ").trim()
                    currentH3 = null
                }
                line.startsWith("### ") -> {
                    documents.flushSection(buffer, currentH2, currentH3, baseMetadata)
                    currentH3 = line.removePrefix("### ").trim()
                }
                else -> buffer += line
            }
        }
        documents.flushSection(buffer, currentH2, currentH3, baseMetadata)
        return documents
    }
}

// buffer 내용을 RagDocument로 변환하여 수신자 리스트에 추가한다.
// 빈 buffer나 헤더가 없는(첫 헤더 이전) 내용은 무시한다.
// id 는 분리된 청크 자체로는 의미가 없으므로 VectorStore 저장 시 자동 생성에 맡긴다 (UUID).
private fun MutableList<RagDocument>.flushSection(
    buffer: MutableList<String>,
    currentH2: String?,
    currentH3: String?,
    baseMetadata: Map<String, Any>,
) {
    val content = buffer.joinToString("\n").trim()
    buffer.clear()
    if (content.isBlank()) return
    val (sectionHeader, depth) = when {
        currentH3 != null -> "${currentH2.orEmpty()} > $currentH3" to "h3"
        currentH2 != null -> currentH2 to "h2"
        else -> return // 첫 ## 헤더 이전 내용 무시
    }
    this += RagDocument(
        id = UUID.randomUUID().toString(),
        text = content,
        metadata = baseMetadata + mapOf("section_header" to sectionHeader, "depth" to depth),
    )
}
