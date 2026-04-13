package stillframe42.aicodereviewer.rag.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper

// RAG 컨벤션 컨텍스트 빌드 서비스
// filePath가 있으면 FileCategoryMapper로 카테고리를 선택해 범위를 좁히고,
// 없으면 전체 문서 대상으로 검색한다.
@Service
class ConventionContextService(
    private val hybridSearchService: HybridConventionSearchService,
) {
    suspend fun buildContext(query: String, filePath: String? = null): String {
        val category = filePath?.let { FileCategoryMapper.selectCategory(it) }
        val docs = hybridSearchService.search(query, topK = 5, category = category)
        if (docs.isEmpty()) return ""
        return docs.joinToString("\n\n---\n\n") { it.text ?: "" }
    }
}
