package stillframe42.aicodereviewer.rag.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.common.observability.withSpan
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper

// RAG 컨벤션 컨텍스트 빌드 서비스
// filePath가 있으면 FileCategoryMapper로 카테고리를 선택해 범위를 좁히고,
// 없으면 전체 문서 대상으로 검색한다.
@Service
class ConventionContextService(
    private val hybridSearchService: HybridConventionSearchService,
    private val observabilityPort: ObservabilityPort,
) {
    suspend fun buildContext(query: String, filePath: String? = null): String =
        observabilityPort.withSpan(
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
            val docs = hybridSearchService.search(query, topK = 5, category = category)
            if (docs.isEmpty()) "" else docs.joinToString("\n\n---\n\n") { it.text ?: "" }
        }
}
