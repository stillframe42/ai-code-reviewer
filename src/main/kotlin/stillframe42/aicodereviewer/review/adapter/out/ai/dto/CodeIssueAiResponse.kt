package stillframe42.aicodereviewer.review.adapter.out.ai.dto

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// Spring AI Structured Output 역직렬화용 어댑터 DTO — 도메인 모델과 AI 응답 형식을 분리
@JsonClassDescription("코드에서 발견된 단일 이슈")
data class CodeIssueAiResponse(
    @field:JsonPropertyDescription("이슈 식별자. 'ISSUE-1', 'ISSUE-2' 형식")
    val id: String,

    @field:JsonPropertyDescription("이슈 카테고리: PERFORMANCE(성능), SECURITY(보안), READABILITY(가독성), ARCHITECTURE(아키텍처)")
    val category: IssueCategory,

    @field:JsonPropertyDescription("이슈가 발생한 라인 번호 (정수). 특정할 수 없으면 null")
    val line: Int?,

    @field:JsonPropertyDescription("이슈 심각도: CRITICAL(운영 장애/보안 취약점), MAJOR(잠재적 버그/성능 저하), MINOR(컨벤션/가독성), SUGGESTION(선택적 개선)")
    val severity: IssueSeverity,

    @field:JsonPropertyDescription("이슈에 대한 한국어 설명")
    val description: String,

    @field:JsonPropertyDescription("구체적인 개선 방법 또는 수정 코드 예시")
    val suggestion: String
) {
    fun toDomain(): CodeIssue = CodeIssue(id, category, line, severity, description, suggestion)
}
