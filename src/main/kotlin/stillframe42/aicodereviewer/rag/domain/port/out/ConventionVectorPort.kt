package stillframe42.aicodereviewer.rag.domain.port.out

import org.springframework.ai.document.Document

// 벡터 저장소 아웃바운드 포트 — VectorStore 구현체를 추상화
interface ConventionVectorPort {
    fun save(documents: List<Document>)
    fun isEmpty(): Boolean
    fun deleteAll()
}
