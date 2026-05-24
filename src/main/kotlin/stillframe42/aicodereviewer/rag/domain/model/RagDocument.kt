package stillframe42.aicodereviewer.rag.domain.model

// RAG 파이프라인의 도메인 표준 문서 표현 — Spring AI Document 결합을 adapter 경계로 격리한다.
// text 는 빈 문자열을 허용 (없는 청크는 빈 문자열로 표현되며 도메인 로직에서 isBlank 체크).
data class RagDocument(
    val id: String,
    val text: String,
    val metadata: Map<String, Any> = emptyMap(),
)
