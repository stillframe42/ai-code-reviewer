package stillframe42.aicodereviewer.rag.adapter.out.ai

import org.springframework.ai.document.Document
import org.springframework.ai.reader.markdown.MarkdownDocumentReader
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

// classpath:conventions/ 하위 마크다운 파일을 읽어 메타데이터를 주입하고 청킹한다.
// 파일명 기반으로 category를 결정하며, 모든 청크에 source/category/version/language를 추가한다.
@Component
class DocumentPreprocessor(
    private val textSplitter: TokenTextSplitter,
) {
    // 파일 경로 → 카테고리 매핑
    private val conventionFiles = mapOf(
        "conventions/kotlin-style.md" to "STYLE",
        "conventions/architecture-guide.md" to "ARCH",
        "conventions/api-design.md" to "API",
        "conventions/security-checklist.md" to "SECURITY",
    )

    // 수평선(---)으로 Document 분리하고 코드 예제를 포함하는 마크다운 읽기 설정
    private val markdownConfig = MarkdownDocumentReaderConfig.builder()
        .withHorizontalRuleCreateDocument(true)
        .withIncludeCodeBlock(true)
        .build()

    fun prepare(): List<Document> {
        val rawDocuments = conventionFiles.flatMap { (path, category) ->
            val fileName = path.substringAfterLast("/")
            MarkdownDocumentReader(ClassPathResource(path), markdownConfig).get()
                .map { doc -> injectMetadata(doc, fileName, category) }
        }
        return textSplitter.apply(rawDocuments)
    }

    // 각 Document에 파일 출처와 카테고리 메타데이터를 추가한다.
    // doc.metadata가 불변일 수 있으므로 새 Map을 합성하여 새 Document를 생성한다.
    private fun injectMetadata(doc: Document, fileName: String, category: String): Document =
        Document(
            doc.text,
            doc.metadata + mapOf(
                "source" to fileName,
                "category" to category,
                "version" to "1.0",
                "language" to "kotlin",
            ),
        )
}
