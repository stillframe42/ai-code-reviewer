package stillframe42.aicodereviewer.review.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.review.domain.model.PrImportance

// PrImportanceAnalyzer 순수 단위 테스트 — Spring 컨텍스트 없이 AntPathMatcher 동작 검증
class PrImportanceAnalyzerTest {

    private val properties = AiReviewerProperties(
        criticalPatterns = listOf(
            "**/*Security*", "**/*Auth*", "**/migration/**", "**/*Config*", "build.gradle.kts"
        )
    )
    private val analyzer = PrImportanceAnalyzer(properties)

    @Test
    fun `Security 파일은 CRITICAL을 반환한다`() {
        assertThat(analyzer.analyze(listOf("src/SecurityConfig.kt"))).isEqualTo(PrImportance.CRITICAL)
    }

    @Test
    fun `Auth 파일은 CRITICAL을 반환한다`() {
        assertThat(analyzer.analyze(listOf("src/AuthService.kt"))).isEqualTo(PrImportance.CRITICAL)
    }

    @Test
    fun `migration 경로 파일은 CRITICAL을 반환한다`() {
        assertThat(
            analyzer.analyze(listOf("src/main/resources/db/migration/V5__add_column.sql"))
        ).isEqualTo(PrImportance.CRITICAL)
    }

    @Test
    fun `Config 파일은 CRITICAL을 반환한다`() {
        assertThat(analyzer.analyze(listOf("src/ReviewConfig.kt"))).isEqualTo(PrImportance.CRITICAL)
    }

    @Test
    fun `build_gradle_kts는 CRITICAL을 반환한다`() {
        assertThat(analyzer.analyze(listOf("build.gradle.kts"))).isEqualTo(PrImportance.CRITICAL)
    }

    @Test
    fun `일반 서비스 파일은 NORMAL을 반환한다`() {
        assertThat(analyzer.analyze(listOf("src/main/kotlin/MyService.kt"))).isEqualTo(PrImportance.NORMAL)
    }

    @Test
    fun `빈 목록은 NORMAL을 반환한다`() {
        assertThat(analyzer.analyze(emptyList())).isEqualTo(PrImportance.NORMAL)
    }

    @Test
    fun `CRITICAL 파일 하나라도 있으면 CRITICAL을 반환한다`() {
        assertThat(
            analyzer.analyze(listOf("src/MyService.kt", "src/SecurityConfig.kt"))
        ).isEqualTo(PrImportance.CRITICAL)
    }
}
