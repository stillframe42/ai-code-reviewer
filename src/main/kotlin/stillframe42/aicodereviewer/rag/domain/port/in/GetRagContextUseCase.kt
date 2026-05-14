package stillframe42.aicodereviewer.rag.domain.port.`in`

interface GetRagContextUseCase {
    suspend fun get(contextId: String): RagContext?
}

data class RagContext(val content: String)
