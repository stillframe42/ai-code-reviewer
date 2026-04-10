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

    @Test
    fun `architecture-guide에 N+1 쿼리 방지 섹션이 포함된다`() {
        val docs = splitter.prepare()
        val archDocs = docs.filter { it.metadata["source"] == "architecture-guide.md" }
        assertThat(archDocs.any { it.text?.contains("@EntityGraph") == true })
            .withFailMessage("@EntityGraph 패턴이 architecture-guide.md 청크에 없음")
            .isTrue()
        assertThat(archDocs.any { it.text?.contains("fetch join") == true })
            .withFailMessage("fetch join 패턴이 architecture-guide.md 청크에 없음")
            .isTrue()
    }

    @Test
    fun `api-design에 null 응답 처리 정책 섹션이 포함된다`() {
        val docs = splitter.prepare()
        val apiDocs = docs.filter { it.metadata["source"] == "api-design.md" }
        assertThat(apiDocs.any { it.text?.contains("JsonInclude") == true })
            .withFailMessage("JsonInclude 패턴이 api-design.md 청크에 없음")
            .isTrue()
        assertThat(apiDocs.any { it.text?.contains("\"string\", \"null\"") == true })
            .withFailMessage("OpenAPI 3.1 null 타입 표현이 api-design.md 청크에 없음")
            .isTrue()
    }

    @Test
    fun `security-checklist A03에 Log Injection 방어 패턴이 포함된다`() {
        val docs = splitter.prepare()
        val secDocs = docs.filter { it.metadata["source"] == "security-checklist.md" }
        assertThat(secDocs.any { it.text?.contains("Log Injection") == true })
            .withFailMessage("Log Injection 패턴이 security-checklist.md 청크에 없음")
            .isTrue()
        assertThat(secDocs.any { it.text?.contains("HtmlUtils") == true })
            .withFailMessage("XSS 방어 패턴(HtmlUtils)이 security-checklist.md 청크에 없음")
            .isTrue()
    }

    @Test
    fun `security-checklist에 로깅 PII 마스킹 섹션이 포함된다`() {
        val docs = splitter.prepare()
        val secDocs = docs.filter { it.metadata["source"] == "security-checklist.md" }
        assertThat(secDocs.any { it.text?.contains("maskEmail") == true })
            .withFailMessage("maskEmail 패턴이 security-checklist.md 청크에 없음")
            .isTrue()
        assertThat(secDocs.any { it.text?.contains("MDC") == true })
            .withFailMessage("MDC 패턴이 security-checklist.md 청크에 없음")
            .isTrue()
    }

    @Test
    fun `kotlin-style에 코루틴 단일 응답과 스트리밍 구분 기준이 포함된다`() {
        val docs = splitter.prepare()
        val kotlinDocs = docs.filter { it.metadata["source"] == "kotlin-style.md" }
        assertThat(kotlinDocs.any {
            val text = it.text ?: ""
            text.contains("suspend fun") && text.contains("Flow") && text.contains("구분 기준")
        })
            .withFailMessage("suspend fun vs Flow 구분 기준이 kotlin-style.md 청크에 없음")
            .isTrue()
    }
}
