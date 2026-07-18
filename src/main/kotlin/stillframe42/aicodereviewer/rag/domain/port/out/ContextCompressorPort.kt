package stillframe42.aicodereviewer.rag.domain.port.out

import stillframe42.aicodereviewer.rag.domain.model.RagDocument

// RAG 검색 결과를 쿼리 관련 부분만 남기도록 압축하는 아웃바운드 포트
// 청크 단위로 압축 LLM에 위임. 압축 결과가 빈 청크는 결과 List에서 제외된다.
// 한 청크 압축 실패 시 해당 청크는 원본 그대로 반환되고 전체 흐름은 계속된다.
interface ContextCompressorPort {
    fun compress(query: String, documents: List<RagDocument>): List<RagDocument>
}
