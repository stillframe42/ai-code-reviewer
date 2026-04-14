package stillframe42.aicodereviewer.rag.domain.port.out

import org.springframework.ai.document.Document
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory

// 벡터 저장소 아웃바운드 포트 — VectorStore 구현체를 추상화
interface ConventionVectorPort {
    fun save(documents: List<Document>)
    fun isEmpty(): Boolean
    fun deleteAll()
    // 쿼리 텍스트를 임베딩하여 cosine 유사도 기반으로 상위 topK 청크를 반환한다.
    // category가 지정되면 metadata.category 기준으로 필터링한다.
    // similarityThreshold 미만의 유사도 문서는 제외한다 (0.0이면 필터링 없음).
    // RRF 혼합 검색에서 호출할 때는 app.rag.similarity-threshold 설정값을 전달한다.
    // 순수 벡터 검색 단독 사용 시에는 호출 지점에서 적절한 값(예: 0.7)을 명시한다.
    fun search(
        query: String,
        topK: Int = 5,
        category: ConventionCategory? = null,
        similarityThreshold: Double = 0.0,
    ): List<Document>
}
