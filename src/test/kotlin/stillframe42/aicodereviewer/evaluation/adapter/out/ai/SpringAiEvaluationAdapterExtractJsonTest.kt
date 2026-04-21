package stillframe42.aicodereviewer.evaluation.adapter.out.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// extractJson()의 bracket counter 정확성 검증 — 한국어 문장 내 중괄호 등 케이스
class SpringAiEvaluationAdapterExtractJsonTest {

    private fun extract(raw: String): String =
        SpringAiEvaluationAdapter.extractJson(raw)

    @Test
    fun `평범한 JSON은 그대로 추출된다`() {
        val raw = """{"score": 0.8, "reason": "좋음"}"""
        assertThat(extract(raw)).isEqualTo(raw)
    }

    @Test
    fun `코드 펜스 래핑 JSON 추출`() {
        val raw = """```json
        {"score": 0.5, "reason": "보통"}
        ```""".trimIndent()
        assertThat(extract(raw)).isEqualTo("""{"score": 0.5, "reason": "보통"}""")
    }

    @Test
    fun `JSON 뒤 trailing 텍스트 제거`() {
        val raw = """{"score": 0.7, "reason": "양호"} 추가 설명입니다."""
        assertThat(extract(raw)).isEqualTo("""{"score": 0.7, "reason": "양호"}""")
    }

    @Test
    fun `한국어 문자열 안에 중괄호가 있어도 정확히 추출`() {
        val raw = """{"score": 0.9, "reason": "코드 패턴 {value}를 잘 사용함"}"""
        assertThat(extract(raw)).isEqualTo(raw)
    }

    @Test
    fun `중첩 객체 정확히 추출`() {
        val raw = """{"score": 0.6, "nested": {"a": 1, "b": {"c": 2}}, "reason": "ok"}"""
        assertThat(extract(raw)).isEqualTo(raw)
    }

    @Test
    fun `이스케이프된 따옴표 안의 중괄호 무시`() {
        val raw = """{"score": 0.5, "reason": "문자열 \"with } brace\" 처리"}"""
        assertThat(extract(raw)).isEqualTo(raw)
    }
}
