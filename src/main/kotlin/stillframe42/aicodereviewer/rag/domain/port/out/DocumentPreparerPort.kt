package stillframe42.aicodereviewer.rag.domain.port.out

import org.springframework.ai.document.Document

// 컨벤션 문서를 청킹하여 Document 리스트로 준비하는 아웃바운드 포트
fun interface DocumentPreparerPort {
    fun prepare(): List<Document>
}
