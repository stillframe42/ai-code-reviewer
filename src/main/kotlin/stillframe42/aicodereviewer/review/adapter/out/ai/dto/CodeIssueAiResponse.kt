package stillframe42.aicodereviewer.review.adapter.out.ai.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import stillframe42.aicodereviewer.review.domain.model.CodeIssue
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// Spring AI Structured Output 역직렬화용 어댑터 DTO — 도메인 모델과 AI 응답 형식을 분리
// AI가 enum 값이나 필드명을 정확히 따르지 않을 수 있으므로 String으로 수신 후 toDomain()에서 안전하게 변환
@JsonClassDescription("코드에서 발견된 단일 이슈")
data class CodeIssueAiResponse(
    @field:JsonPropertyDescription("이슈 식별자. 'ISSUE-1', 'ISSUE-2' 형식")
    val id: String? = null,

    @field:JsonPropertyDescription("이슈 카테고리: PERFORMANCE(성능), SECURITY(보안), READABILITY(가독성), ARCHITECTURE(아키텍처)")
    val category: String = "READABILITY",

    @field:JsonPropertyDescription("이슈가 발생한 파일 경로 (예: src/main/kotlin/Foo.kt). 특정할 수 없으면 null")
    val filename: String?,

    @field:JsonPropertyDescription("이슈가 발생한 라인 번호 (정수). 특정할 수 없으면 null")
    val line: Int?,

    // AI가 "severity" 대신 "level" 필드명을 사용하는 경우를 대비해 alias 추가
    @field:JsonAlias("level")
    @field:JsonPropertyDescription("이슈 심각도: CRITICAL(운영 장애/보안 취약점), MAJOR(잠재적 버그/성능 저하), MINOR(컨벤션/가독성), SUGGESTION(선택적 개선)")
    val severity: String = "MINOR",

    @field:JsonPropertyDescription("이슈에 대한 한국어 설명")
    val description: String = "",

    @field:JsonPropertyDescription("구체적인 개선 방법 또는 수정 코드 예시")
    val suggestion: String = "",
) {
    fun toDomain(): CodeIssue = CodeIssue(
        id = id ?: "ISSUE-?",
        category = parseCategory(category),
        filename = filename,
        line = line,
        severity = parseSeverity(severity),
        description = description,
        suggestion = suggestion,
    )

    // AI가 한국어 자유 형식 또는 대소문자 혼용 값을 반환하는 경우 fallback으로 READABILITY 사용
    private fun parseCategory(value: String): IssueCategory =
        runCatching { IssueCategory.valueOf(value.trim().uppercase().replace(' ', '_')) }
            .getOrDefault(IssueCategory.READABILITY)

    // AI가 "error"/"warning"/"info" 등 비표준 값을 반환하는 경우 가장 유사한 enum으로 매핑
    private fun parseSeverity(value: String): IssueSeverity = when (value.trim().lowercase()) {
        "critical" -> IssueSeverity.CRITICAL
        "major", "error" -> IssueSeverity.MAJOR
        "minor", "warning" -> IssueSeverity.MINOR
        "suggestion", "info", "note" -> IssueSeverity.SUGGESTION
        else -> runCatching { IssueSeverity.valueOf(value.trim().uppercase()) }.getOrDefault(IssueSeverity.MINOR)
    }
}
