package stillframe42.aicodereviewer.rag.domain.port.`in`

// RAG 컨텍스트 단건 조회 인바운드 포트 — Python 에이전트의 lazy fetch 시 호출됨
interface GetRagContextUseCase {
    suspend fun get(contextId: String): RagContext?
}

// RAG 컨텍스트 단건 조회 결과 — content 만 노출 (응답 스키마와 일치)
data class RagContext(val content: String)
