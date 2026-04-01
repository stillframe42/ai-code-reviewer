package stillframe42.aicodereviewer.review.adapter.out.ai.dto

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import stillframe42.aicodereviewer.review.domain.model.CodeReview

// Spring AI Structured Output 역직렬화용 어댑터 DTO — 도메인 모델과 AI 응답 형식을 분리
@JsonClassDescription("코드 리뷰 전체 결과")
data class CodeReviewAiResponse(
    @field:JsonPropertyDescription("전반적인 코드 품질 점수 (1~10, 10이 최고)")
    val overall_score: Int = 0,

    @field:JsonPropertyDescription("코드 전반에 대한 한국어 총평 요약")
    val summary: String,

    @field:JsonPropertyDescription("발견된 이슈 목록. 이슈가 없으면 빈 배열")
    val issues: List<CodeIssueAiResponse>,

    // AI가 "{title, description}" 객체 배열로 반환하는 경우도 처리
    @field:JsonPropertyDescription("코드의 잘된 점 목록 (한국어). 없으면 빈 배열")
    @field:JsonDeserialize(contentUsing = PositiveItemDeserializer::class)
    val positives: List<String> = emptyList(),
) {
    fun toDomain(): CodeReview = CodeReview(overall_score, summary, issues.map { it.toDomain() }, positives)
}

// positives 항목이 단순 String 또는 {title, description} 객체 둘 다 처리
internal class PositiveItemDeserializer : StdDeserializer<String>(String::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String =
        if (p.currentToken == JsonToken.START_OBJECT) {
            val node = p.codec.readTree<ObjectNode>(p)
            node.get("title")?.asText() ?: node.get("description")?.asText() ?: ""
        } else {
            p.text ?: ""
        }
}
