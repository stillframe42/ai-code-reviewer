package stillframe42.aicodereviewer.review.domain.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions

// DiffPreprocessor 순수 단위 테스트 — Spring 컨텍스트 없이 도메인 로직만 검증
class DiffPreprocessorTest {

    private val preprocessor = DiffPreprocessor()

    @Test
    fun `바이너리 파일 청크 제거`() {
        val diff = """
            |diff --git a/image.png b/image.png
            |index abc..def 100644
            |Binary files a/image.png and b/image.png differ
            |diff --git a/Foo.kt b/Foo.kt
            |index 123..456 100644
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1,3 +1,3 @@
            | fun foo() {
            |-    return 1
            |+    return 2
            | }
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        assertFalse(result.diff.contains("image.png"), "바이너리 파일은 diff에서 제거되어야 한다")
        assertTrue(result.filteredFiles.contains("image.png"), "제거된 파일 목록에 포함되어야 한다")
        assertTrue(result.diff.contains("Foo.kt"), "바이너리가 아닌 파일은 유지되어야 한다")
    }

    @Test
    fun `테스트 파일 패턴 필터 동작`() {
        val diff = """
            |diff --git a/FooTest.kt b/FooTest.kt
            |--- a/FooTest.kt
            |+++ b/FooTest.kt
            |@@ -1,3 +1,3 @@
            |-old
            |+new
            |diff --git a/Foo.kt b/Foo.kt
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1,3 +1,3 @@
            |-old
            |+new
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions(filterTestFiles = true))

        assertFalse(result.diff.contains("FooTest.kt"), "테스트 파일은 제거되어야 한다")
        assertTrue(result.filteredFiles.contains("FooTest.kt"), "제거된 파일 목록에 포함되어야 한다")
        assertTrue(result.diff.contains("Foo.kt"), "일반 파일은 유지되어야 한다")
    }

    @Test
    fun `테스트 파일 필터 비활성화 시 테스트 파일 포함`() {
        val diff = """
            |diff --git a/FooTest.kt b/FooTest.kt
            |--- a/FooTest.kt
            |+++ b/FooTest.kt
            |@@ -1 +1 @@
            |-old
            |+new
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions(filterTestFiles = false))

        assertTrue(result.diff.contains("FooTest.kt"), "필터 비활성화 시 테스트 파일도 포함되어야 한다")
        assertTrue(result.filteredFiles.isEmpty(), "제거된 파일이 없어야 한다")
    }

    @Test
    fun `잠금 파일 패턴 필터 동작`() {
        val diff = """
            |diff --git a/yarn.lock b/yarn.lock
            |--- a/yarn.lock
            |+++ b/yarn.lock
            |@@ -1 +1 @@
            |-old
            |+new
            |diff --git a/Foo.kt b/Foo.kt
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1 +1 @@
            |-old
            |+new
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions(filterLockFiles = true))

        assertFalse(result.diff.contains("yarn.lock"), "잠금 파일은 제거되어야 한다")
        assertTrue(result.filteredFiles.any { it.contains("yarn.lock") }, "제거된 파일 목록에 포함되어야 한다")
        assertTrue(result.diff.contains("Foo.kt"), "일반 파일은 유지되어야 한다")
    }

    @Test
    fun `메타데이터 줄 제거 (diff --git, index, new file mode)`() {
        val diff = """
            |diff --git a/Foo.kt b/Foo.kt
            |index abc123..def456 100644
            |new file mode 100644
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1,3 +1,3 @@
            |-old
            |+new
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions())

        assertFalse(result.diff.contains("diff --git"), "diff --git 줄은 제거되어야 한다")
        assertFalse(result.diff.contains("index abc123"), "index 줄은 제거되어야 한다")
        assertFalse(result.diff.contains("new file mode"), "new file mode 줄은 제거되어야 한다")
        // --- / +++ 는 파일 컨텍스트를 위해 유지
        assertTrue(result.diff.contains("--- a/Foo.kt"), "--- 파일 헤더는 유지되어야 한다")
        assertTrue(result.diff.contains("+++ b/Foo.kt"), "+++ 파일 헤더는 유지되어야 한다")
    }

    @Test
    fun `contextLines=0 일 때 context 줄 제거, @@ 헤더 유지`() {
        val diff = """
            |diff --git a/Foo.kt b/Foo.kt
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1,6 +1,6 @@
            | context before 1
            | context before 2
            |-old line
            |+new line
            | context after 1
            | context after 2
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions(contextLines = 0))

        assertFalse(result.diff.contains("context before"), "contextLines=0 시 앞 context 줄은 제거되어야 한다")
        assertFalse(result.diff.contains("context after"), "contextLines=0 시 뒤 context 줄은 제거되어야 한다")
        assertTrue(result.diff.contains("@@"), "@@ hunk 헤더는 유지되어야 한다")
        assertTrue(result.diff.contains("-old line"), "삭제 줄은 유지되어야 한다")
        assertTrue(result.diff.contains("+new line"), "추가 줄은 유지되어야 한다")
    }

    @Test
    fun `contextLines=1 일 때 변경 줄 인접 1줄만 유지`() {
        val diff = """
            |diff --git a/Foo.kt b/Foo.kt
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1,5 +1,5 @@
            | far context
            | near context
            |-old line
            |+new line
            | near context after
            | far context after
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions(contextLines = 1))

        assertFalse(result.diff.contains("far context\n"), "변경 줄에서 2줄 이상 떨어진 context는 제거되어야 한다")
        assertTrue(result.diff.contains("near context"), "변경 줄에서 1줄 이내 context는 유지되어야 한다")
        assertTrue(result.diff.contains("-old line"), "삭제 줄은 유지되어야 한다")
        assertTrue(result.diff.contains("+new line"), "추가 줄은 유지되어야 한다")
    }

    @Test
    fun `사용자 정의 제외 패턴 동작`() {
        val diff = """
            |diff --git a/generated/Foo.java b/generated/Foo.java
            |--- a/generated/Foo.java
            |+++ b/generated/Foo.java
            |@@ -1 +1 @@
            |-old
            |+new
            |diff --git a/Foo.kt b/Foo.kt
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1 +1 @@
            |-old
            |+new
        """.trimMargin()

        val result = preprocessor.preprocess(
            diff,
            DiffFilterOptions(additionalExcludePatterns = listOf("generated/**")),
        )

        assertFalse(result.diff.contains("Foo.java"), "사용자 정의 패턴에 매칭된 파일은 제거되어야 한다")
        assertTrue(result.filteredFiles.any { it.contains("Foo.java") }, "제거된 파일 목록에 포함되어야 한다")
        assertTrue(result.diff.contains("Foo.kt"), "패턴에 매칭되지 않은 파일은 유지되어야 한다")
    }

    @Test
    fun `토큰 추정 전후 값이 올바른지`() {
        val diff = """
            |diff --git a/FooTest.kt b/FooTest.kt
            |--- a/FooTest.kt
            |+++ b/FooTest.kt
            |@@ -1 +1 @@
            |-old
            |+new
            |diff --git a/Foo.kt b/Foo.kt
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1 +1 @@
            |-old
            |+new
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions(filterTestFiles = true))

        assertTrue(result.estimatedTokensBefore > 0, "전처리 전 토큰 수는 양수여야 한다")
        assertTrue(result.estimatedTokensAfter >= 0, "전처리 후 토큰 수는 0 이상이어야 한다")
        assertTrue(result.estimatedTokensAfter < result.estimatedTokensBefore, "전처리 후 토큰이 줄어야 한다")
        assertTrue(result.savedTokens >= 0, "절감 토큰 수는 0 이상이어야 한다")
        assertTrue(result.reductionPercent in 0..100, "절감률은 0~100% 범위여야 한다")
    }

    // ─── 토큰 한도 기반 자르기 테스트 ───────────────────────────────────────

    @Test
    fun `maxTokens가 충분히 크면 모든 청크 유지`() {
        val diff = makeDiff("A.kt", changedLines = 3) + makeDiff("B.kt", changedLines = 2)

        val result = preprocessor.preprocess(diff, DiffFilterOptions(maxTokens = 100_000))

        assertTrue(result.diff.contains("A.kt"), "A.kt는 유지되어야 한다")
        assertTrue(result.diff.contains("B.kt"), "B.kt는 유지되어야 한다")
        assertEquals(0, result.filteredFiles.size, "토큰 한도로 제거된 파일이 없어야 한다")
    }

    @Test
    fun `maxTokens 초과 시 변경량 적은 청크 제거`() {
        // A.kt: 변경 10줄, B.kt: 변경 1줄
        val chunkA = makeDiff("A.kt", changedLines = 10)
        val chunkB = makeDiff("B.kt", changedLines = 1)
        val diff = chunkA + chunkB

        // A.kt 청크만 수용하는 토큰 한도 (B.kt 청크 토큰 수보다 약간 작게)
        val aOnlyTokens = estimateTokens(chunkA) + 5

        val result = preprocessor.preprocess(diff, DiffFilterOptions(maxTokens = aOnlyTokens))

        assertTrue(result.diff.contains("A.kt"), "변경량 많은 A.kt는 유지되어야 한다")
        assertFalse(result.diff.contains("B.kt"), "변경량 적은 B.kt는 제거되어야 한다")
        assertTrue(result.filteredFiles.contains("B.kt"), "B.kt가 filteredFiles에 포함되어야 한다")
    }

    @Test
    fun `변경량 기준 내림차순 정렬이 diff 순서에 반영`() {
        // 먼저 추가된 X.kt는 변경 1줄, 나중 추가된 Y.kt는 변경 5줄
        val diff = makeDiff("X.kt", changedLines = 1) + makeDiff("Y.kt", changedLines = 5)

        // 두 파일 모두 수용 가능한 한도
        val result = preprocessor.preprocess(diff, DiffFilterOptions(maxTokens = 100_000))

        // 변경량 내림차순 정렬이면 Y.kt가 X.kt보다 앞에 나온다
        val yPos = result.diff.indexOf("Y.kt")
        val xPos = result.diff.indexOf("X.kt")
        assertTrue(yPos < xPos, "변경량 많은 Y.kt가 X.kt보다 앞에 위치해야 한다")
    }

    @Test
    fun `maxTokens=null이면 자르기 미적용`() {
        val diff = makeDiff("A.kt", changedLines = 3) + makeDiff("B.kt", changedLines = 2)

        val result = preprocessor.preprocess(diff, DiffFilterOptions(maxTokens = null))

        assertTrue(result.diff.contains("A.kt"))
        assertTrue(result.diff.contains("B.kt"))
    }

    @Test
    fun `maxTokens가 극히 작으면 모든 청크 제거`() {
        val diff = makeDiff("A.kt", changedLines = 3) + makeDiff("B.kt", changedLines = 2)

        val result = preprocessor.preprocess(diff, DiffFilterOptions(maxTokens = 1))

        assertTrue(result.filteredFiles.containsAll(listOf("A.kt", "B.kt")), "모든 파일이 filteredFiles에 포함되어야 한다")
    }

    @Test
    fun `패턴 필터 + 토큰 한도 복합 적용`() {
        val testChunk = makeDiff("FooTest.kt", changedLines = 5)
        val bigChunk = makeDiff("Main.kt", changedLines = 10)
        val smallChunk = makeDiff("Util.kt", changedLines = 1)
        val diff = testChunk + bigChunk + smallChunk

        // Main.kt 청크만 수용하는 토큰 한도
        val mainOnlyTokens = estimateTokens(bigChunk) + 5

        val result = preprocessor.preprocess(
            diff,
            DiffFilterOptions(filterTestFiles = true, maxTokens = mainOnlyTokens),
        )

        assertFalse(result.diff.contains("FooTest.kt"), "테스트 파일은 패턴 필터로 제거되어야 한다")
        assertTrue(result.diff.contains("Main.kt"), "변경량 많은 Main.kt는 유지되어야 한다")
        assertFalse(result.diff.contains("Util.kt"), "변경량 적은 Util.kt는 토큰 한도로 제거되어야 한다")
        assertTrue(result.filteredFiles.containsAll(listOf("FooTest.kt", "Util.kt")))
    }

    @Test
    fun `토큰 한도로 제거된 파일이 filteredFiles에 포함`() {
        val diff = makeDiff("A.kt", changedLines = 5) + makeDiff("B.kt", changedLines = 1)

        val result = preprocessor.preprocess(diff, DiffFilterOptions(maxTokens = 1))

        assertTrue(result.filteredFiles.isNotEmpty(), "filteredFiles가 비어있지 않아야 한다")
        assertTrue(
            result.filteredFiles.any { it == "A.kt" || it == "B.kt" },
            "제거된 파일명이 filteredFiles에 포함되어야 한다",
        )
    }

    // 테스트용 diff 청크 생성 헬퍼 — fileName 기준으로 changedLines 수만큼 +줄을 포함한다
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

    // TokenEstimator와 동일한 방식으로 토큰 수 추정 (4자 ≈ 1토큰)
    private fun estimateTokens(text: String): Int = kotlin.math.ceil(text.length / 4.0).toInt()

    @Test
    fun `diffOptions null이 아닐 때 전처리 결과가 원본보다 짧거나 같음`() {
        val diff = """
            |diff --git a/Foo.kt b/Foo.kt
            |index abc..def 100644
            |--- a/Foo.kt
            |+++ b/Foo.kt
            |@@ -1,5 +1,5 @@
            | ctx1
            | ctx2
            |-old
            |+new
            | ctx3
            | ctx4
        """.trimMargin()

        val result = preprocessor.preprocess(diff, DiffFilterOptions(contextLines = 0))

        assertTrue(result.diff.length <= diff.length, "전처리 결과는 원본보다 길 수 없다")
    }
}
