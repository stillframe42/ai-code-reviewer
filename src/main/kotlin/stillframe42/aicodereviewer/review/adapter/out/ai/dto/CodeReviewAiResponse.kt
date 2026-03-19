package stillframe42.aicodereviewer.review.adapter.out.ai.dto

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import stillframe42.aicodereviewer.review.domain.model.CodeReview

// Spring AI Structured Output 역직렬화용 어댑터 DTO — 도메인 모델과 AI 응답 형식을 분리
@JsonClassDescription("코드 리뷰 전체 결과")
data class CodeReviewAiResponse(
    @JsonPropertyDescription("전반적인 코드 품질 점수 (1~10, 10이 최고)")
    val score: Int,

    @JsonPropertyDescription("코드 전반에 대한 한국어 총평 요약")
    val summary: String,

    @JsonPropertyDescription("발견된 이슈 목록. 이슈가 없으면 빈 배열")
    val issues: List<CodeIssueAiResponse>,

    @JsonPropertyDescription("코드의 잘된 점 목록 (한국어). 없으면 빈 배열")
    val positives: List<String>
) {
    fun toDomain(): CodeReview = CodeReview(score, summary, issues.map { it.toDomain() }, positives)
}
