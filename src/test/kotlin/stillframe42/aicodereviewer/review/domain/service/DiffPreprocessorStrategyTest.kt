package stillframe42.aicodereviewer.review.domain.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.FileReviewStrategy

// DiffPreprocessor 전략 적용 단위 테스트
// FileExtensionClassifier 기반 전략 분류, QUERY_REVIEW 헤더 삽입, strategyOverrides 적용을 검증한다
class DiffPreprocessorStrategyTest {

    private val preprocessor = DiffPreprocessor

    // ─── QUERY_REVIEW 헤더 삽입 ───────────────────────────────────────────────

    @Test
    fun `yml 파일 청크에 QUERY_REVIEW 헤더 삽입`() {
        val diff = makeDiff("application.yml", changedLines = 2)

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        assertTrue(
            result.diff.contains("# [QUERY_REVIEW]"),
            "yml 파일 청크에 QUERY_REVIEW 헤더가 삽입되어야 한다",
        )
        assertTrue(result.diff.contains("application.yml"), "파일명이 헤더에 포함되어야 한다")
    }

    @Test
    fun `kt 파일 청크에 QUERY_REVIEW 헤더 미삽입`() {
        val diff = makeDiff("Foo.kt", changedLines = 2)

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        assertFalse(
            result.diff.contains("# [QUERY_REVIEW]"),
            "kt 파일 청크에는 QUERY_REVIEW 헤더가 없어야 한다",
        )
    }

    @Test
    fun `sql 파일 청크에 QUERY_REVIEW 헤더 삽입`() {
        val diff = makeDiff("V1__init.sql", changedLines = 3)

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        assertTrue(result.diff.contains("# [QUERY_REVIEW]"), "sql 파일에 QUERY_REVIEW 헤더가 삽입되어야 한다")
    }

    // ─── SKIP 전략 파일 제외 ─────────────────────────────────────────────────

    @Test
    fun `SKIP 전략 파일은 filteredFiles에 추가되고 diff에서 제거`() {
        val diff = makeDiff("logo.png", changedLines = 1) + makeDiff("Foo.kt", changedLines = 2)

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        assertFalse(result.diff.contains("logo.png"), "png 파일은 diff에서 제거되어야 한다")
        assertTrue(result.filteredFiles.contains("logo.png"), "png 파일이 filteredFiles에 포함되어야 한다")
        assertTrue(result.diff.contains("Foo.kt"), "kt 파일은 유지되어야 한다")
    }

    @Test
    fun `gradlew 파일은 SKIP 전략으로 제외`() {
        val diff = makeDiff("gradlew", changedLines = 1) + makeDiff("Foo.kt", changedLines = 1)

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        assertFalse(result.diff.contains("gradlew\n"), "gradlew는 diff에서 제거되어야 한다")
        assertTrue(result.filteredFiles.contains("gradlew"), "gradlew가 filteredFiles에 포함되어야 한다")
    }

    // ─── strategyOverrides 적용 ──────────────────────────────────────────────

    @Test
    fun `strategyOverrides가 FileExtensionClassifier보다 우선 적용`() {
        // kt 파일은 기본적으로 FullReview이지만 SKIP으로 오버라이드
        val diff = makeDiff("Foo.kt", changedLines = 2) + makeDiff("Bar.kt", changedLines = 2)

        val result = preprocessor.preprocess(
            diff,
            DiffFilterOptions(strategyOverrides = mapOf("Foo.kt" to FileReviewStrategy.Skip)),
        )

        assertFalse(result.diff.contains("Foo.kt"), "오버라이드된 Foo.kt는 제거되어야 한다")
        assertTrue(result.filteredFiles.contains("Foo.kt"), "Foo.kt가 filteredFiles에 포함되어야 한다")
        assertTrue(result.diff.contains("Bar.kt"), "오버라이드되지 않은 Bar.kt는 유지되어야 한다")
    }

    @Test
    fun `strategyOverrides glob 패턴으로 kt 파일을 QueryReview로 강제`() {
        val diff = makeDiff("src/main/kotlin/Foo.kt", changedLines = 2)

        val result = preprocessor.preprocess(
            diff,
            DiffFilterOptions(strategyOverrides = mapOf("src/main/kotlin/**" to FileReviewStrategy.QueryReview)),
        )

        assertTrue(
            result.diff.contains("# [QUERY_REVIEW]"),
            "glob 오버라이드로 kt 파일도 QUERY_REVIEW 헤더를 가져야 한다",
        )
    }

    // ─── glob 필터가 strategyOverrides보다 우선 ───────────────────────────────

    @Test
    fun `shouldExclude glob 필터가 strategyOverrides보다 우선 적용`() {
        // yml을 additionalExcludePatterns에 명시적으로 추가하면 strategyOverrides(FullReview)보다 우선
        val diff = makeDiff("application.yml", changedLines = 2)

        val result = preprocessor.preprocess(
            diff,
            DiffFilterOptions(
                additionalExcludePatterns = listOf("**/*.yml"),
                strategyOverrides = mapOf("**/*.yml" to FileReviewStrategy.FullReview),
            ),
        )

        assertFalse(result.diff.contains("application.yml"), "glob 제외 필터가 먼저 적용되어 yml이 제거되어야 한다")
        assertTrue(
            result.filteredFiles.any { it.contains("application.yml") },
            "application.yml이 filteredFiles에 포함되어야 한다",
        )
    }

    // ─── 토큰 추정 ───────────────────────────────────────────────────────────

    @Test
    fun `QUERY_REVIEW 헤더 삽입 후 토큰 추정값이 증가하거나 동일`() {
        val diff = makeDiff("application.yml", changedLines = 2)

        // 헤더 없이 전처리 (yml을 SKIP으로 오버라이드하지 않고, 단순 비교를 위해 kt 파일 diff도 사용)
        val resultYml = preprocessor.preprocess(diff, DiffFilterOptions())
        val resultKt = preprocessor.preprocess(makeDiff("Foo.kt", changedLines = 2), DiffFilterOptions())

        // yml은 헤더가 삽입되므로 동일 내용의 kt보다 토큰이 더 많아야 한다
        assertTrue(
            resultYml.estimatedTokensAfter >= resultKt.estimatedTokensAfter,
            "QUERY_REVIEW 헤더로 인해 yml 처리 결과 토큰이 kt보다 크거나 같아야 한다",
        )
    }

    // ─── mixed diff (FULL + QUERY + SKIP 혼합) ────────────────────────────────

    @Test
    fun `mixed diff에서 각 파일이 올바른 전략으로 처리`() {
        val diff = makeDiff("Foo.kt", changedLines = 2) +
            makeDiff("application.yml", changedLines = 2) +
            makeDiff("logo.png", changedLines = 1)

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        // FullReview: 헤더 없이 유지
        assertTrue(result.diff.contains("Foo.kt"), "kt 파일은 유지되어야 한다")

        // QueryReview: 헤더 삽입 후 유지
        assertTrue(result.diff.contains("application.yml"), "yml 파일은 유지되어야 한다")
        assertTrue(result.diff.contains("# [QUERY_REVIEW]"), "yml 파일에 QUERY_REVIEW 헤더가 있어야 한다")

        // Skip: 제거
        assertFalse(result.diff.contains("logo.png"), "png 파일은 제거되어야 한다")
        assertTrue(result.filteredFiles.contains("logo.png"), "png 파일이 filteredFiles에 있어야 한다")
    }

    // 테스트용 diff 청크 생성 헬퍼
    private fun makeDiff(fileName: String, changedLines: Int): String {
        val changes = (1..changedLines).joinToString("\n") { "+changed line $it" }
        return """
            |diff --git a/$fileName b/$fileName
            |--- a/$fileName
            |+++ b/$fileName
            |@@ -1,$changedLines +1,$changedLines @@
            |$changes
            |
        """.trimMargin()
    }
}
