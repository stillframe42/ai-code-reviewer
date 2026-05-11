package stillframe42.aicodereviewer.agent.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.model.AgentFinding
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

class AgentFindingMapperTest {

    @Test
    fun `severity 표준 매핑 - HIGH MEDIUM LOW INFO 와 enum 이름 모두 변환된다`() {
        val raws = listOf("HIGH", "critical", "Medium", "MAJOR", "low", "MINOR", "info", "SUGGESTION")
        val expected = listOf(
            IssueSeverity.CRITICAL, IssueSeverity.CRITICAL,
            IssueSeverity.MAJOR, IssueSeverity.MAJOR,
            IssueSeverity.MINOR, IssueSeverity.MINOR,
            IssueSeverity.SUGGESTION, IssueSeverity.SUGGESTION,
        )

        val mapped = raws.map { rawSev ->
            val result = AgentAnalysisResult(
                analysisId = "id",
                status = "DONE",
                findings = listOf(finding(severity = rawSev)),
            )
            AgentFindingMapper.toCodeReview(result).issues[0].severity
        }

        assertThat(mapped).containsExactlyElementsOf(expected)
    }

    @Test
    fun `알 수 없는 severity 는 SUGGESTION 으로 fallback 된다`() {
        val result = AgentAnalysisResult(
            analysisId = "id",
            status = "DONE",
            findings = listOf(finding(severity = "WEIRD"), finding(severity = "")),
        )

        val severities = AgentFindingMapper.toCodeReview(result).issues.map { it.severity }
        assertThat(severities).containsExactly(IssueSeverity.SUGGESTION, IssueSeverity.SUGGESTION)
    }

    @Test
    fun `location file colon line 형식이 filename 과 line 으로 파싱된다`() {
        val result = AgentAnalysisResult(
            analysisId = "id",
            status = "DONE",
            findings = listOf(finding(location = "src/Foo.kt:42")),
        )

        val issue = AgentFindingMapper.toCodeReview(result).issues[0]
        assertThat(issue.filename).isEqualTo("src/Foo.kt")
        assertThat(issue.line).isEqualTo(42)
    }

    @Test
    fun `location 에 콜론이 없으면 filename 만 채우고 line 은 null 이다`() {
        val result = AgentAnalysisResult(
            analysisId = "id",
            status = "DONE",
            findings = listOf(finding(location = "stub")),
        )

        val issue = AgentFindingMapper.toCodeReview(result).issues[0]
        assertThat(issue.filename).isEqualTo("stub")
        assertThat(issue.line).isNull()
    }

    @Test
    fun `findings 가 비어 있으면 overallScore 는 10 이고 summary 에 analysisId 가 포함된다`() {
        val result = AgentAnalysisResult(analysisId = "agent-007", status = "DONE", findings = emptyList())

        val codeReview = AgentFindingMapper.toCodeReview(result)

        assertThat(codeReview.overallScore).isEqualTo(10)
        assertThat(codeReview.summary).contains("agent-007")
        assertThat(codeReview.issues).isEmpty()
        assertThat(codeReview.positives).isEmpty()
    }

    @Test
    fun `owaspReference 는 description 끝에 부착되고 null 이면 부착되지 않는다`() {
        val result = AgentAnalysisResult(
            analysisId = "id",
            status = "DONE",
            findings = listOf(
                finding(description = "SQL injection", owaspReference = "A03:2021"),
                finding(description = "Style nit", owaspReference = null),
            ),
        )

        val issues = AgentFindingMapper.toCodeReview(result).issues
        assertThat(issues[0].description).isEqualTo("SQL injection (OWASP: A03:2021)")
        assertThat(issues[1].description).isEqualTo("Style nit")
        assertThat(issues[0].category).isEqualTo(IssueCategory.SECURITY)
    }

    private fun finding(
        severity: String = "INFO",
        type: String = "STUB",
        location: String = "stub:1",
        description: String = "desc",
        suggestion: String = "fix",
        owaspReference: String? = null,
    ): AgentFinding = AgentFinding(
        severity = severity,
        type = type,
        location = location,
        description = description,
        suggestion = suggestion,
        owaspReference = owaspReference,
    )
}
