package stillframe42.aicodereviewer.review.dto

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

@JsonClassDescription("코드에서 발견된 단일 이슈")
data class CodeIssue(
    @JsonPropertyDescription("이슈가 발생한 파일 경로 또는 코드 위치. 위치를 특정할 수 없으면 null")
    val location: String?,

    @JsonPropertyDescription("이슈 심각도: CRITICAL(버그/보안), WARNING(잠재적 문제), INFO(개선 제안)")
    val severity: IssueSeverity,

    @JsonPropertyDescription("이슈에 대한 한국어 설명")
    val description: String,

    @JsonPropertyDescription("구체적인 개선 방법 또는 수정 코드 예시")
    val suggestion: String
)
