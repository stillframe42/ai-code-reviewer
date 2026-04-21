package stillframe42.aicodereviewer.rag.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.common.observability.withSpan
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper

@Service
class ConventionContextService(
    private val hybridSearchService: HybridConventionSearchService,
    private val observabilityPort: ObservabilityPort,
    @param:Value("\${rag.enabled:true}") private val ragEnabled: Boolean,
) {
    suspend fun buildContext(query: String, filePath: String? = null): String {
        if (!ragEnabled) return ""
        return observabilityPort.withSpan(
            name = "rag.context",
            input = mapOf("query" to query, "filePath" to (filePath ?: "")),
            outputMapper = { result: String ->
                val category = filePath?.let { FileCategoryMapper.selectCategory(it) }
                mapOf(
                    "category" to (category?.name ?: "ALL"),
                    "documentCount" to if (result.isEmpty()) 0 else result.split("\n\n---\n\n").size,
                    "contextLength" to result.length,
                )
            },
        ) {
            val category = filePath?.let { FileCategoryMapper.selectCategory(it) }
            // topK·threshold는 RagProperties default(B-1·B-2 sweep 결과 채택값) 사용
            val docs = hybridSearchService.search(query, category = category)
            if (docs.isEmpty()) "" else docs.joinToString("\n\n---\n\n") { it.text ?: "" }
        }
    }
}
