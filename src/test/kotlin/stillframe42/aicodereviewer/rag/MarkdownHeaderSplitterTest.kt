package stillframe42.aicodereviewer.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.rag.adapter.out.ai.MarkdownHeaderSplitter

class MarkdownHeaderSplitterTest {

    private val splitter = MarkdownHeaderSplitter()
    private val baseMetadata = mapOf("source" to "test.md", "category" to "TEST")

    @Test
    fun `## 헤더 기준으로 Document가 분리된다`() {
        val text = """
            # 문서 제목
            intro 내용

            ## 섹션 1
            섹션 1 본문입니다.

            ## 섹션 2
            섹션 2 본문입니다.
        """.trimIndent()

        val docs = splitter.split(text, baseMetadata)

        assertThat(docs).hasSize(2)
        assertThat(docs[0].metadata["section_header"]).isEqualTo("섹션 1")
        assertThat(docs[0].metadata["depth"]).isEqualTo("h2")
        assertThat(docs[0].text).contains("섹션 1 본문입니다.")
        assertThat(docs[1].metadata["section_header"]).isEqualTo("섹션 2")
    }

    @Test
    fun `### 헤더는 부모 ## 헤더를 section_header에 포함한다`() {
        val text = """
            ## Null 안전성

            ### Non-null 타입 우선
            non-null 타입을 사용한다.

            ### !! 연산자 금지
            강제 언박싱을 지양한다.
        """.trimIndent()

        val docs = splitter.split(text, baseMetadata)

        assertThat(docs).hasSize(2)
        assertThat(docs[0].metadata["section_header"]).isEqualTo("Null 안전성 > Non-null 타입 우선")
        assertThat(docs[0].metadata["depth"]).isEqualTo("h3")
        assertThat(docs[1].metadata["section_header"]).isEqualTo("Null 안전성 > !! 연산자 금지")
    }

    @Test
    fun `코드 블록 내 ## 는 헤더로 인식하지 않는다`() {
        val text = """
            ## 섹션 1
            아래는 코드 예시입니다.

            ```kotlin
            // ## 이것은 주석입니다
            val x = 1
            ```

            본문 계속.
        """.trimIndent()

        val docs = splitter.split(text, baseMetadata)

        assertThat(docs).hasSize(1)
        assertThat(docs[0].text).contains("// ## 이것은 주석입니다")
    }

    @Test
    fun `본문이 없는 섹션은 Document를 생성하지 않는다`() {
        val text = """
            ## 섹션 1

            ### 서브섹션 1
            서브섹션 내용입니다.
        """.trimIndent()

        val docs = splitter.split(text, baseMetadata)

        // "## 섹션 1" 다음 빈 줄만 있고 바로 "### 서브섹션 1"이 나오므로 h2 Document는 생성되지 않음
        assertThat(docs).hasSize(1)
        assertThat(docs[0].metadata["depth"]).isEqualTo("h3")
    }

    @Test
    fun `첫 ## 헤더 이전 내용은 Document에 포함하지 않는다`() {
        val text = """
            # 문서 제목
            이 내용은 헤더 전입니다.

            ## 첫 섹션
            섹션 내용.
        """.trimIndent()

        val docs = splitter.split(text, baseMetadata)

        assertThat(docs).hasSize(1)
        assertThat(docs[0].metadata["section_header"]).isEqualTo("첫 섹션")
    }

    @Test
    fun `baseMetadata가 모든 Document에 포함된다`() {
        val text = """
            ## 섹션 A
            내용 A.
        """.trimIndent()

        val docs = splitter.split(text, baseMetadata)

        assertThat(docs[0].metadata["source"]).isEqualTo("test.md")
        assertThat(docs[0].metadata["category"]).isEqualTo("TEST")
    }
}
