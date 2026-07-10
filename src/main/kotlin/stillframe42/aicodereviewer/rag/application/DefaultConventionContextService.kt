package stillframe42.aicodereviewer.rag.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.common.observability.withSpan
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionContextUseCase
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper

@Service
class DefaultConventionContextService(
    private val hybridSearchService: HybridConventionSearchService,
    private val observabilityPort: ObservabilityPort,
    @param:Value("\${rag.enabled:true}") private val ragEnabled: Boolean,
) : ConventionContextUseCase {
    override suspend fun buildContext(query: String, filePath: String?): String {
        if (!ragEnabled) return ""

        val category = filePath?.let { FileCategoryMapper.selectCategory(it) }
        return observabilityPort.withSpan(
            name = "rag.context",
            input = mapOf("query" to query, "filePath" to (filePath ?: "")),
            outputMapper = { result: String ->
                mapOf(
                    "category" to category.nameOrAll(),
                    "documentCount" to if (result.isEmpty()) 0 else result.split("\n\n---\n\n").size,
                    "contextLength" to result.length,
                )
            },
        ) {
            val docs = hybridSearchService.search(query = query, category = category)
            if (docs.isEmpty()) "" else docs.joinToString("\n\n---\n\n") { it.text ?: "" }
        }
    }

    override suspend fun buildContextIds(query: String, filePath: String?): List<String> {
        if (!ragEnabled) return emptyList()

        val category = filePath?.let { FileCategoryMapper.selectCategory(it) }
        return observabilityPort.withSpan(
            name = "rag.context.ids",
            input = mapOf("query" to query, "filePath" to (filePath ?: "")),
            outputMapper = { result: List<String> ->
                mapOf("category" to category.nameOrAll(), "idCount" to result.size)
            },
        ) {
            hybridSearchService.search(query = query, category = category).map { it.id }
        }
    }
}
