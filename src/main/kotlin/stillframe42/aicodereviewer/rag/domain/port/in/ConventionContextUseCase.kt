package stillframe42.aicodereviewer.rag.domain.port.`in`

// 컨벤션 RAG 컨텍스트 빌드 인바운드 포트 — chat/review/agent 가 소비한다
interface ConventionContextUseCase {
    suspend fun buildContext(query: String, filePath: String? = null): String

    suspend fun buildContextIds(query: String, filePath: String? = null): List<String>
}
