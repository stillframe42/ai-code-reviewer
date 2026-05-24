package stillframe42.aicodereviewer.rag.adapter.out.ai

import org.springframework.ai.transformer.splitter.TokenTextSplitter
import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import java.util.UUID

// TokenTextSplitter(512)로 기본 청크를 생성한 후 인접 청크 간 문자 단위 overlap을 후처리로 적용한다.
// Spring AI 2.0.0-M2의 TokenTextSplitter.Builder는 overlap을 지원하지 않으므로 직접 구현한다.
// 실험 전용 — 프로덕션에서는 MarkdownHeaderSplitter(DocumentPreparerPort 구현체) 사용.
class OverlappingTokenSplitter(
    private val chunkSize: Int = 512,
    private val overlapChars: Int = 200, // ≈ 50 tokens (50 × 4자/token 근사)
) {
    // 모든 컨벤션 파일을 TokenTextSplitter(chunkSize) + overlap 후처리로 분리하여 RagDocument 리스트 반환
    fun prepare(): List<RagDocument> {
        val splitter = TokenTextSplitter.builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(50)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build()
        val baseChunks = DocumentPreprocessor(splitter).prepare()
        return splitWithOverlap(baseChunks)
    }

    // 인접 청크 간 overlap을 적용한다.
    // source 메타데이터가 다른 경우(파일 경계)에는 overlap을 적용하지 않는다.
    internal fun splitWithOverlap(docs: List<RagDocument>): List<RagDocument> =
        docs.mapIndexed { i, doc ->
            val isFirstInSource = i == 0 || docs[i - 1].metadata["source"] != doc.metadata["source"]
            if (isFirstInSource) doc
            else {
                val overlap = docs[i - 1].text.takeLast(overlapChars)
                doc.copy(
                    id = UUID.randomUUID().toString(),
                    text = overlap + "\n" + doc.text,
                )
            }
        }
}
