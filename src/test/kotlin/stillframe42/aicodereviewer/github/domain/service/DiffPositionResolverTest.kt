package stillframe42.aicodereviewer.github.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// DiffPositionResolver 단위 테스트 — Spring 컨텍스트 없이 순수 도메인 로직 검증
class DiffPositionResolverTest {

    private val resolver = DiffPositionResolver()

    // --- buildPositionIndex 테스트 ---

    @Test
    fun `단일 hunk에서 추가 라인의 position을 올바르게 인덱싱한다`() {
        val diff = """
            diff --git a/Foo.kt b/Foo.kt
            --- a/Foo.kt
            +++ b/Foo.kt
            @@ -1,3 +1,4 @@
             line1
             line2
            +added line
             line3
        """.trimIndent()

        val index = resolver.buildPositionIndex(diff)

        // @@ = position 1, line1(new=1) = 2, line2(new=2) = 3, added(new=3) = 4, line3(new=4) = 5
        assertThat(index["Foo.kt"]).containsEntry(1, 2)
        assertThat(index["Foo.kt"]).containsEntry(2, 3)
        assertThat(index["Foo.kt"]).containsEntry(3, 4)
        assertThat(index["Foo.kt"]).containsEntry(4, 5)
    }

    @Test
    fun `삭제 라인은 position을 소비하지만 newLine 인덱스에 등록되지 않는다`() {
        val diff = """
            diff --git a/Foo.kt b/Foo.kt
            --- a/Foo.kt
            +++ b/Foo.kt
            @@ -1,3 +1,2 @@
             context
            -deleted line
             context2
        """.trimIndent()

        val index = resolver.buildPositionIndex(diff)

        // @@ = position 1, context(new=1) = 2, deleted = 3(position only), context2(new=2) = 4
        assertThat(index["Foo.kt"]).containsEntry(1, 2)
        assertThat(index["Foo.kt"]).containsEntry(2, 4)
        // 삭제 라인(구 파일 라인 2)은 새 파일에 없으므로 인덱스에 없어야 한다
        assertThat(index["Foo.kt"]).doesNotContainKey(0)
    }

    @Test
    fun `다중 hunk에서 position이 연속으로 카운팅된다`() {
        val diff = """
            diff --git a/Foo.kt b/Foo.kt
            --- a/Foo.kt
            +++ b/Foo.kt
            @@ -1,2 +1,2 @@
             context1
            +added1
            @@ -10,2 +11,2 @@
             context10
            +added11
        """.trimIndent()

        val index = resolver.buildPositionIndex(diff)

        // 첫 @@ = position 1, context1(new=1) = 2, added1(new=2) = 3
        // 두 번째 @@ = position 4, context10(new=11) = 5, added11(new=12) = 6
        assertThat(index["Foo.kt"]).containsEntry(1, 2)
        assertThat(index["Foo.kt"]).containsEntry(2, 3)
        assertThat(index["Foo.kt"]).containsEntry(11, 5)
        assertThat(index["Foo.kt"]).containsEntry(12, 6)
    }

    @Test
    fun `--- a 와 +++ b 줄은 position 카운트에 포함되지 않는다`() {
        val diff = """
            diff --git a/Foo.kt b/Foo.kt
            --- a/Foo.kt
            +++ b/Foo.kt
            @@ -1,1 +1,1 @@
            +added
        """.trimIndent()

        val index = resolver.buildPositionIndex(diff)

        // @@ = position 1, added(new=1) = 2
        // --- / +++ 줄이 position에 포함됐다면 added는 position 4가 됨
        assertThat(index["Foo.kt"]).containsEntry(1, 2)
    }

    @Test
    fun `파일 경로가 슬래시 포함 전체 경로로 인덱싱된다`() {
        val diff = """
            diff --git a/src/main/kotlin/Foo.kt b/src/main/kotlin/Foo.kt
            --- a/src/main/kotlin/Foo.kt
            +++ b/src/main/kotlin/Foo.kt
            @@ -5,1 +5,2 @@
             existing
            +new line
        """.trimIndent()

        val index = resolver.buildPositionIndex(diff)

        assertThat(index).containsKey("src/main/kotlin/Foo.kt")
        assertThat(index["src/main/kotlin/Foo.kt"]).containsEntry(6, 3)
    }

    @Test
    fun `멀티 파일 diff에서 파일별 position이 독립적으로 초기화된다`() {
        val diff = """
            diff --git a/A.kt b/A.kt
            --- a/A.kt
            +++ b/A.kt
            @@ -1,1 +1,2 @@
             lineA
            +addedA
            diff --git a/B.kt b/B.kt
            --- a/B.kt
            +++ b/B.kt
            @@ -1,1 +1,2 @@
             lineB
            +addedB
        """.trimIndent()

        val index = resolver.buildPositionIndex(diff)

        // A.kt: @@ = 1, lineA(new=1) = 2, addedA(new=2) = 3
        // B.kt: @@ = 1 (파일 경계에서 초기화), lineB(new=1) = 2, addedB(new=2) = 3
        assertThat(index["A.kt"]).containsEntry(2, 3)
        assertThat(index["B.kt"]).containsEntry(2, 3)
    }

    // --- parseNewStart 테스트 ---

    @Test
    fun `hunk 헤더에서 새 파일 시작 라인을 파싱한다`() {
        assertThat(resolver.parseNewStart("@@ -10,6 +10,7 @@")).isEqualTo(10)
        assertThat(resolver.parseNewStart("@@ -1,0 +1 @@")).isEqualTo(1)
        assertThat(resolver.parseNewStart("@@ -100,3 +200,5 @@ fun main()")).isEqualTo(200)
    }

    // --- resolve 테스트 ---

    @Test
    fun `line이 null인 이슈는 unmappedIssues에 포함된다`() {
        val diff = singleFileDiff("Foo.kt")
        val issue = issue(filename = "Foo.kt", line = null)

        val result = resolver.resolve(diff, listOf(issue))

        assertThat(result.lineComments).isEmpty()
        assertThat(result.unmappedIssues).containsExactly(issue)
    }

    @Test
    fun `filename과 line이 매핑 가능하면 lineComments에 포함된다`() {
        val diff = """
            diff --git a/Foo.kt b/Foo.kt
            --- a/Foo.kt
            +++ b/Foo.kt
            @@ -1,2 +1,3 @@
             context
            +added line
             context2
        """.trimIndent()
        // added line은 new_line=2, position=3
        val issue = issue(filename = "Foo.kt", line = 2)

        val result = resolver.resolve(diff, listOf(issue))

        assertThat(result.lineComments).hasSize(1)
        assertThat(result.lineComments[0].path).isEqualTo("Foo.kt")
        assertThat(result.lineComments[0].position).isEqualTo(3)
        assertThat(result.unmappedIssues).isEmpty()
    }

    @Test
    fun `filename이 null인 이슈는 단일 파일 PR에서 자동으로 파일을 추론한다`() {
        val diff = """
            diff --git a/Foo.kt b/Foo.kt
            --- a/Foo.kt
            +++ b/Foo.kt
            @@ -1,1 +1,2 @@
             context
            +added
        """.trimIndent()
        // added = new_line=2, position=3
        val issue = issue(filename = null, line = 2)

        val result = resolver.resolve(diff, listOf(issue))

        assertThat(result.lineComments).hasSize(1)
        assertThat(result.lineComments[0].path).isEqualTo("Foo.kt")
        assertThat(result.unmappedIssues).isEmpty()
    }

    @Test
    fun `filename이 null이고 여러 파일에 동일한 라인이 존재하면 unmappedIssues에 포함된다`() {
        val diff = """
            diff --git a/A.kt b/A.kt
            --- a/A.kt
            +++ b/A.kt
            @@ -1,1 +1,2 @@
             context
            +added
            diff --git a/B.kt b/B.kt
            --- a/B.kt
            +++ b/B.kt
            @@ -1,1 +1,2 @@
             context
            +added
        """.trimIndent()
        // line=2는 A.kt와 B.kt 모두에 존재 → 추론 불가
        val issue = issue(filename = null, line = 2)

        val result = resolver.resolve(diff, listOf(issue))

        assertThat(result.lineComments).isEmpty()
        assertThat(result.unmappedIssues).containsExactly(issue)
    }

    @Test
    fun `diff에 존재하지 않는 라인 번호의 이슈는 unmappedIssues에 포함된다`() {
        val diff = singleFileDiff("Foo.kt")
        val issue = issue(filename = "Foo.kt", line = 9999)

        val result = resolver.resolve(diff, listOf(issue))

        assertThat(result.lineComments).isEmpty()
        assertThat(result.unmappedIssues).containsExactly(issue)
    }

    @Test
    fun `이슈 목록이 비어있으면 빈 결과를 반환한다`() {
        val result = resolver.resolve(singleFileDiff("Foo.kt"), emptyList())

        assertThat(result.lineComments).isEmpty()
        assertThat(result.unmappedIssues).isEmpty()
    }

    @Test
    fun `인라인 코멘트 본문에 severity 이모지와 description이 포함된다`() {
        val diff = """
            diff --git a/Foo.kt b/Foo.kt
            --- a/Foo.kt
            +++ b/Foo.kt
            @@ -1,1 +1,2 @@
             context
            +added
        """.trimIndent()
        val issue = issue(
            filename = "Foo.kt",
            line = 2,
            severity = IssueSeverity.CRITICAL,
            description = "SQL Injection 취약점",
            suggestion = "PreparedStatement를 사용하세요",
        )

        val result = resolver.resolve(diff, listOf(issue))

        val body = result.lineComments[0].body
        assertThat(body).contains("🔴")
        assertThat(body).contains("CRITICAL")
        assertThat(body).contains("SQL Injection 취약점")
        assertThat(body).contains("PreparedStatement를 사용하세요")
    }

    // --- 헬퍼 ---

    private fun singleFileDiff(filename: String) = """
        diff --git a/$filename b/$filename
        --- a/$filename
        +++ b/$filename
        @@ -1,2 +1,3 @@
         context
        +added
         context2
    """.trimIndent()

    private fun issue(
        filename: String?,
        line: Int?,
        severity: IssueSeverity = IssueSeverity.MINOR,
        description: String = "테스트 이슈",
        suggestion: String = "수정 제안",
    ) = CodeIssue(
        id = "ISSUE-1",
        category = IssueCategory.READABILITY,
        filename = filename,
        line = line,
        severity = severity,
        description = description,
        suggestion = suggestion,
    )
}
