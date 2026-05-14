package stillframe42.aicodereviewer.rag.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.rag.domain.port.`in`.GetRagContextUseCase
import stillframe42.aicodereviewer.rag.domain.port.`in`.RagContext
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

@Service
class DefaultGetRagContextService(
    private val vectorPort: ConventionVectorPort,
) : GetRagContextUseCase {
    override suspend fun get(contextId: String): RagContext? =
        vectorPort.findById(contextId)?.let { RagContext(content = it.text ?: "") }
}
