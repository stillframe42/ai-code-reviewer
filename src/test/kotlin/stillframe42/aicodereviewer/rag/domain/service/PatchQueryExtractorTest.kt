package stillframe42.aicodereviewer.rag.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PatchQueryExtractorTest {

    private val extractor = PatchQueryExtractor()

    @Test
    fun `추가된 코드 라인 첫 N줄 추출 (파일명 fallback 없음)`() {
        val patch = """
            @@ -10,3 +10,6 @@ class UserService {
                 fun existing() = 1
            +    fun new_function() = 2
            +    val Wrong_Naming = 3
            +    import foo.bar.*
                 fun other() = 4
        """.trimIndent()

        val query = extractor.extract(patch, filePath = "src/main/kotlin/UserService.kt", maxLines = 5)

        assertThat(query).contains("fun new_function()")
        assertThat(query).contains("val Wrong_Naming")
        assertThat(query).contains("import foo.bar.*")
        assertThat(query).doesNotContain("fun existing()")
        assertThat(query).doesNotContain("@@")
    }

    @Test
    fun `추가 라인 없으면 파일명으로 fallback`() {
        val patch = """
            @@ -5,3 +5,3 @@
                 fun a() = 1
            -    fun b() = 2
                 fun c() = 3
        """.trimIndent()

        val query = extractor.extract(patch, filePath = "src/main/kotlin/UserService.kt", maxLines = 5)

        assertThat(query).isEqualTo("UserService.kt")
    }

    @Test
    fun `filePath 없으면 추가 라인만 반환, 둘 다 없으면 빈 문자열`() {
        val query1 = extractor.extract("+    val x = 1", filePath = null, maxLines = 5)
        assertThat(query1).isEqualTo("val x = 1")

        val query2 = extractor.extract("", filePath = null, maxLines = 5)
        assertThat(query2).isEmpty()
    }

    @Test
    fun `maxLines 초과 시 잘라냄`() {
        val patch = (1..10).joinToString("\n") { "+    val v$it = $it" }
        val query = extractor.extract(patch, filePath = null, maxLines = 3)
        assertThat(query.lines()).hasSize(3)
        assertThat(query).contains("val v1")
        assertThat(query).contains("val v3")
        assertThat(query).doesNotContain("val v4")
    }
}
