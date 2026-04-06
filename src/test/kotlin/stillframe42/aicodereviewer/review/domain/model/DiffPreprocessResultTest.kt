package stillframe42.aicodereviewer.review.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// DiffPreprocessResult.fileNames 계산 프로퍼티 순수 단위 테스트
class DiffPreprocessResultTest {

    private fun result(vararg fileDiffs: String) = DiffPreprocessResult(
        fileDiffs = fileDiffs.toList(),
        estimatedTokensBefore = 0,
        estimatedTokensAfter = 0,
        filteredFiles = emptyList(),
    )

    @Test
    fun `수정 파일 diff에서 파일명을 추출한다`() {
        val r = result(
            """
            --- a/src/SecurityConfig.kt
            +++ b/src/SecurityConfig.kt
            @@ -1,1 +1,2 @@
             class SecurityConfig
            +    // 보안 강화
            """.trimIndent()
        )
        assertThat(r.fileNames).containsExactly("src/SecurityConfig.kt")
    }

    @Test
    fun `신규 파일은 +++ b에서 파일명을 추출한다`() {
        val r = result(
            """
            --- /dev/null
            +++ b/src/NewFile.kt
            @@ -0,0 +1,1 @@
            +class NewFile
            """.trimIndent()
        )
        assertThat(r.fileNames).containsExactly("src/NewFile.kt")
    }

    @Test
    fun `여러 청크에서 파일명 목록을 반환한다`() {
        val r = result(
            """
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -1,1 +1,1 @@
            -old
            +new
            """.trimIndent(),
            """
            --- a/src/Bar.kt
            +++ b/src/Bar.kt
            @@ -1,1 +1,1 @@
            -old
            +new
            """.trimIndent(),
        )
        assertThat(r.fileNames).containsExactly("src/Foo.kt", "src/Bar.kt")
    }

    @Test
    fun `빈 fileDiffs이면 빈 목록을 반환한다`() {
        assertThat(result().fileNames).isEmpty()
    }
}
