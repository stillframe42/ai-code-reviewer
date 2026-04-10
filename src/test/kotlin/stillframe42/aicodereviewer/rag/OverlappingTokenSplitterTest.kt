package stillframe42.aicodereviewer.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import stillframe42.aicodereviewer.rag.adapter.out.ai.OverlappingTokenSplitter

class OverlappingTokenSplitterTest {

    // overlapChars=10으로 작게 설정하여 테스트 가독성 향상
    private val splitter = OverlappingTokenSplitter(overlapChars = 10)

    private fun doc(text: String, source: String) =
        Document(text, mapOf<String, Any>("source" to source))

    @Test
    fun `빈 리스트는 빈 리스트를 반환한다`() {
        assertThat(splitter.splitWithOverlap(emptyList())).isEmpty()
    }

    @Test
    fun `단일 문서는 overlap 없이 그대로 반환한다`() {
        val result = splitter.splitWithOverlap(listOf(doc("hello world", "a.md")))
        assertThat(result).hasSize(1)
        assertThat(result[0].text).isEqualTo("hello world")
    }

    @Test
    fun `첫 번째 청크는 overlap이 없다`() {
        val docs = listOf(
            doc("first chunk text", "a.md"),
            doc("second chunk text", "a.md"),
        )
        val result = splitter.splitWithOverlap(docs)
        assertThat(result[0].text).isEqualTo("first chunk text")
    }

    @Test
    fun `두 번째 청크는 이전 청크 꼬리 overlapChars자가 앞에 붙는다`() {
        // "first chunk"(11자).takeLast(10) = "irst chunk"
        val docs = listOf(
            doc("first chunk", "a.md"),
            doc("second chunk", "a.md"),
        )
        val result = splitter.splitWithOverlap(docs)
        assertThat(result[1].text).isEqualTo("irst chunk\nsecond chunk")
    }

    @Test
    fun `파일 경계(source 변경)에서는 overlap이 적용되지 않는다`() {
        val docs = listOf(
            doc("last chunk of file A", "a.md"),
            doc("first chunk of file B", "b.md"),
        )
        val result = splitter.splitWithOverlap(docs)
        assertThat(result[1].text).isEqualTo("first chunk of file B")
    }
}
