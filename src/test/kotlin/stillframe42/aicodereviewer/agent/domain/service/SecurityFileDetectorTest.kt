package stillframe42.aicodereviewer.agent.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrFileStatus

class SecurityFileDetectorTest {

    @Test
    fun `보안 파일 단일 - hasSecurityFile true 그리고 firstSecurityFile 반환`() {
        val files = listOf(prFile("src/SecurityConfig.kt"))

        assertThat(SecurityFileDetector.hasSecurityFile(files)).isTrue()
        assertThat(SecurityFileDetector.firstSecurityFile(files)?.filename).isEqualTo("src/SecurityConfig.kt")
    }

    @Test
    fun `보안과 일반 혼합 - hasSecurityFile true 그리고 firstSecurityFile 은 보안 파일`() {
        val files = listOf(
            prFile("src/UserController.kt"),
            prFile("src/JwtFilter.kt"),
            prFile("src/OrderRepository.kt"),
        )

        assertThat(SecurityFileDetector.hasSecurityFile(files)).isTrue()
        assertThat(SecurityFileDetector.firstSecurityFile(files)?.filename).isEqualTo("src/JwtFilter.kt")
    }

    @Test
    fun `모두 일반 파일 - hasSecurityFile false 그리고 firstSecurityFile null`() {
        val files = listOf(
            prFile("src/UserController.kt"),
            prFile("src/OrderRepository.kt"),
        )

        assertThat(SecurityFileDetector.hasSecurityFile(files)).isFalse()
        assertThat(SecurityFileDetector.firstSecurityFile(files)).isNull()
    }

    @Test
    fun `빈 리스트 - hasSecurityFile false 그리고 firstSecurityFile null`() {
        assertThat(SecurityFileDetector.hasSecurityFile(emptyList())).isFalse()
        assertThat(SecurityFileDetector.firstSecurityFile(emptyList())).isNull()
    }

    private fun prFile(name: String): PrFile = PrFile(
        filename = name,
        status = PrFileStatus.MODIFIED,
        additions = 1,
        deletions = 0,
        changes = 1,
        patch = null,
        previousFilename = null,
    )
}
