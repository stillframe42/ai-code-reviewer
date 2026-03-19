package stillframe42.aicodereviewer.review.adapter.out.ai.dto

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// Spring AI Structured Output 역직렬화용 어댑터 DTO — 도메인 모델과 AI 응답 형식을 분리
@JsonClassDescription("코드에서 발견된 단일 이슈")
data class CodeIssueAiResponse(
    @field:JsonPropertyDescription("이슈가 발생한 파일 경로 또는 코드 위치. 위치를 특정할 수 없으면 null")
    val location: String?,

    @field:JsonPropertyDescription("이슈 심각도: CRITICAL(버그/보안), WARNING(잠재적 문제), INFO(개선 제안)")
    val severity: IssueSeverity,

    @field:JsonPropertyDescription("이슈에 대한 한국어 설명")
    val description: String,

    @field:JsonPropertyDescription("구체적인 개선 방법 또는 수정 코드 예시")
    val suggestion: String
) {
    fun toDomain(): CodeIssue = CodeIssue(location, severity, description, suggestion)
}
