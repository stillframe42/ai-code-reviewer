package stillframe42.aicodereviewer.review.adapter.out.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity

// 운영 컨버터(reviewResponseConverter)의 lenient 파싱 계약을 검증한다.
// Jackson 3(GA) 전환 시 이 계약이 재현되는지가 마이그레이션 롤백 기준 1호였다.
class CodeReviewAiResponseParsingTest {

    @Test
    fun `백슬래시-달러 이스케이프가 포함된 JSON을 허용한다`() {
        val json = """{"summary": "코드에 \${'$'}variable 패턴 사용", "issues": []}"""

        val result = reviewResponseConverter.convert(json)

        assertThat(result.summary).isEqualTo("코드에 \$variable 패턴 사용")
    }

    @Test
    fun `알 수 없는 필드는 무시한다`() {
        val json = """{"summary": "요약", "issues": [], "title": "미지의 필드", "suggestions": ["x"]}"""

        val result = reviewResponseConverter.convert(json)

        assertThat(result.summary).isEqualTo("요약")
    }

    @Test
    fun `positives 는 문자열과 객체 형식이 혼합되어도 파싱한다`() {
        val json = """
            {
              "summary": "요약",
              "issues": [],
              "positives": ["명확한 네이밍", {"title": "테스트 커버리지", "description": "설명"}, {"description": "설명만 있는 항목"}]
            }
        """.trimIndent()

        val result = reviewResponseConverter.convert(json)

        assertThat(result.positives).containsExactly("명확한 네이밍", "테스트 커버리지", "설명만 있는 항목")
    }

    @Test
    fun `overall_score 와 positives 생략 시 기본값으로 파싱한다`() {
        // GA 스키마는 기본값 보유 필드를 required 에서 제외하므로 모델이 생략할 수 있다
        val json = """{"summary": "요약", "issues": [{"level": "MAJOR", "description": "이슈", "filename": null, "line": null}]}"""

        val result = reviewResponseConverter.convert(json)

        assertThat(result.overall_score).isEqualTo(0)
        assertThat(result.positives).isEmpty()
        assertThat(result.toDomain().issues.single().severity).isEqualTo(IssueSeverity.MAJOR)
    }
}
