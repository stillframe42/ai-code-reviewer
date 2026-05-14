package stillframe42.aicodereviewer.review.comparison

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewModeRequest
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

class ComparisonReportWriterTest {

    @Test
    fun `두 비교 결과로 마크다운 보고서 필수 섹션이 생성된다`() {
        val simpleResult = ComparisonResult(
            mode = ReviewModeRequest.WITHOUT_TOOLS,
            review = CodeReview(
                overallScore = 7,
                summary = "전반적으로 양호합니다.",
                issues = emptyList(),
                positives = listOf("명확한 네이밍"),
                toolCallCount = 0,
            ),
            latencyMs = 1500L,
            estimatedOutputTokens = 100,
        )
        val toolsResult = ComparisonResult(
            mode = ReviewModeRequest.WITH_TOOLS,
            review = CodeReview(
                overallScore = 6,
                summary = "보안 이슈가 발견되었습니다.",
                issues = listOf(
                    CodeIssue(
                        id = "s1",
                        category = IssueCategory.SECURITY,
                        line = 5,
                        severity = IssueSeverity.MAJOR,
                        description = "SQL Injection 위험",
                        suggestion = "PreparedStatement 사용",
                    ),
                ),
                positives = listOf("테스트 커버리지 양호"),
                toolCallCount = 2,
            ),
            latencyMs = 4200L,
            estimatedOutputTokens = 280,
        )

        val report = ComparisonReportWriter.generate(simpleResult, toolsResult, "owner/test-repo", 5)

        // 헤더 및 섹션 존재 확인
        assertThat(report).contains("WITHOUT_TOOLS")
        assertThat(report).contains("WITH_TOOLS")
        assertThat(report).contains("owner/test-repo")
        assertThat(report).contains("#5")
        // 비교 표 수치 확인
        assertThat(report).contains("| 이슈 감지 수 | 0 | 1 |")
        assertThat(report).contains("| Tool 호출 횟수 | 0 | 2 |")
        assertThat(report).contains("1500")
        assertThat(report).contains("4200")
        // 카테고리 분포 확인
        assertThat(report).contains("SECURITY")
        // 관찰 포인트 확인
        assertThat(report).contains("+1 건 차이")
    }

    @Test
    fun `이슈가 없으면 평균 description 길이는 0이다`() {
        val result = ComparisonResult(
            mode = ReviewModeRequest.WITHOUT_TOOLS,
            review = CodeReview(
                overallScore = 9,
                summary = "깔끔한 코드입니다.",
                issues = emptyList(),
                positives = listOf("간결한 로직"),
                toolCallCount = 0,
            ),
            latencyMs = 800L,
            estimatedOutputTokens = 50,
        )

        val report = ComparisonReportWriter.generate(result, result, "owner/repo", 1)

        assertThat(report).contains("| issue description 평균 길이 | 0 | 0 |")
    }
}
