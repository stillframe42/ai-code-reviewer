package stillframe42.aicodereviewer.agent.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.github.domain.model.PrFileStatus

class PayloadSamplesTest {

    @Test
    fun `SAMPLES 는 정확히 3건이며 ID 가 고유하다`() {
        assertThat(PayloadSamples.SAMPLES).hasSize(3)
        assertThat(PayloadSamples.SAMPLES.map { it.id })
            .containsExactly("security-sql", "arch-jpa", "style-long")
    }

    @Test
    fun `각 샘플 patch 파일은 존재한다`() {
        PayloadSamples.SAMPLES.forEach { sample ->
            val text = PayloadSamples.readPatch(sample)
            assertThat(text)
                .withFailMessage("샘플 ${sample.id} 의 patch 파일이 비어있다")
                .isNotEmpty()
        }
    }

    @Test
    fun `patch 에서 + + + b 헤더를 모두 PrFile 로 추출한다`() {
        val patch = """
            diff --git a/src/foo/A.kt b/src/foo/A.kt
            new file mode 100644
            --- /dev/null
            +++ b/src/foo/A.kt
            @@ -0,0 +1,2 @@
            +package foo
            +class A
            diff --git a/src/bar/B.kt b/src/bar/B.kt
            new file mode 100644
            --- /dev/null
            +++ b/src/bar/B.kt
            @@ -0,0 +1,1 @@
            +package bar
        """.trimIndent()

        val files = PayloadSamples.parsePrFiles(patch)

        assertThat(files).hasSize(2)
        assertThat(files.map { it.filename })
            .containsExactly("src/foo/A.kt", "src/bar/B.kt")
        assertThat(files).allSatisfy { assertThat(it.status).isEqualTo(PrFileStatus.ADDED) }
    }

    @Test
    fun `security-sql 샘플 patch 의 첫 파일은 SecurityAuditRepository 경로다`() {
        val patch = PayloadSamples.readPatch(PayloadSamples.SAMPLES.first { it.id == "security-sql" })
        val files = PayloadSamples.parsePrFiles(patch)
        assertThat(files).isNotEmpty()
        assertThat(files.first().filename).contains("SecurityAuditRepository")
    }
}
