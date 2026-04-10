package stillframe42.aicodereviewer.rag.adapter.out.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.transformer.splitter.TokenTextSplitter

// DocumentPreprocessor 단위 테스트 — Spring 컨텍스트 없이 실행
// TokenTextSplitter를 직접 생성하여 주입한다.
class DocumentPreprocessorTest {

    private val splitter = TokenTextSplitter.builder()
        .withChunkSize(512)
        .withMinChunkSizeChars(100)
        .withMinChunkLengthToEmbed(50)
        .withMaxNumChunks(10000)
        .withKeepSeparator(true)
        .build()

    private val preprocessor = DocumentPreprocessor(splitter)

    @Test
    fun `prepare() 호출 시 1건 이상의 Document가 반환된다`() {
        val documents = preprocessor.prepare()
        assertThat(documents).isNotEmpty
    }

    @Test
    fun `모든 Document에 source 메타데이터가 포함된다`() {
        val documents = preprocessor.prepare()
        assertThat(documents).allMatch { it.metadata.containsKey("source") }
    }

    @Test
    fun `모든 Document에 category 메타데이터가 포함된다`() {
        val documents = preprocessor.prepare()
        assertThat(documents).allMatch { it.metadata.containsKey("category") }
    }

    @Test
    fun `category 값이 STYLE, ARCH, API, SECURITY 중 하나이다`() {
        val documents = preprocessor.prepare()
        val validCategories = setOf("STYLE", "ARCH", "API", "SECURITY")
        assertThat(documents).allMatch { validCategories.contains(it.metadata["category"]) }
    }

    @Test
    fun `version과 language 메타데이터가 포함된다`() {
        val documents = preprocessor.prepare()
        assertThat(documents).allMatch {
            it.metadata["version"] == "1.0" && it.metadata["language"] == "kotlin"
        }
    }
}
