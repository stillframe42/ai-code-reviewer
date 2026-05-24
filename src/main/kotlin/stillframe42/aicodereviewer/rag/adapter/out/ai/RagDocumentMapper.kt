package stillframe42.aicodereviewer.rag.adapter.out.ai

import org.springframework.ai.document.Document
import stillframe42.aicodereviewer.rag.domain.model.RagDocument

// adapter 경계에서 Spring AI Document ↔ 도메인 RagDocument 매핑을 일원화한다.
internal fun Document.toRagDocument(): RagDocument = RagDocument(
    id = id,
    text = text.orEmpty(),
    metadata = metadata,
)

internal fun RagDocument.toSpringAiDocument(): Document = Document.builder()
    .id(id)
    .text(text)
    .metadata(metadata)
    .build()
