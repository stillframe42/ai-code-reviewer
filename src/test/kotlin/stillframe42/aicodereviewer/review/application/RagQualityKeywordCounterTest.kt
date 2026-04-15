package stillframe42.aicodereviewer.review.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// RagQualityComparisonIT.countKeywords / countByKeyword 전용 순수 단위 테스트.
// Spring 컨텍스트 불필요 — 단순 regex 유틸리티를 검증하는 용도이므로
// 프로젝트 규칙("Service 이상은 @SpringBootTest") 예외에 해당한다.
class RagQualityKeywordCounterTest {

    @Test
    fun `영문 키워드 port가 단독으로 나오면 1회 매칭된다`() {
        val result = RagQualityComparisonIT.countByKeyword("port")
        assertThat(result["port"]).isEqualTo(1)
        assertThat(RagQualityComparisonIT.countKeywords("port")).isEqualTo(1)
    }

    @Test
    fun `영문 키워드 port는 import substring과 매칭되지 않는다`() {
        // 핵심 regression 케이스 — 기존 split 방식은 이 케이스에서 1로 오집계했다
        val result = RagQualityComparisonIT.countByKeyword("import Foo")
        assertThat(result["port"]).isEqualTo(0)
        assertThat(RagQualityComparisonIT.countKeywords("import Foo")).isEqualTo(0)
    }

    @Test
    fun `영문 키워드 port는 ImportResource 같은 확장 단어와도 매칭되지 않는다`() {
        val result = RagQualityComparisonIT.countByKeyword("@ImportResource")
        assertThat(result["port"]).isEqualTo(0)
        assertThat(RagQualityComparisonIT.countKeywords("@ImportResource")).isEqualTo(0)
    }

    @Test
    fun `소문자 default는 대문자 Default 키워드와 매칭되지 않는다`() {
        // case-sensitive 전환 검증 — 기존 ignoreCase=true는 소문자 default도 잡았다
        val result = RagQualityComparisonIT.countByKeyword("default value 처리")
        assertThat(result["Default"]).isEqualTo(0)
        assertThat(RagQualityComparisonIT.countKeywords("default value 처리")).isEqualTo(0)
    }

    @Test
    fun `대문자 Default는 정확히 매칭된다`() {
        val result = RagQualityComparisonIT.countByKeyword("Default prefix 를 붙여야 한다")
        assertThat(result["Default"]).isEqualTo(1)
        assertThat(RagQualityComparisonIT.countKeywords("Default prefix 를 붙여야 한다")).isEqualTo(1)
    }

    @Test
    fun `카멜케이스 UseCase는 한글 조사와 인접해도 매칭된다`() {
        val result = RagQualityComparisonIT.countByKeyword("UseCase 인터페이스에 의존해야 합니다")
        assertThat(result["UseCase"]).isEqualTo(1)
    }

    @Test
    fun `한글 키워드 컨벤션은 조사 뒤에서도 매칭된다`() {
        val result = RagQualityComparisonIT.countByKeyword("컨벤션을 따르세요")
        assertThat(result["컨벤션"]).isEqualTo(1)
    }

    @Test
    fun `영문 키워드 hexagonal은 단어 내부 매칭을 허용한다`() {
        val result = RagQualityComparisonIT.countByKeyword("hexagonal architecture")
        assertThat(result["hexagonal"]).isEqualTo(1)
    }

    @Test
    fun `빈 문자열에서는 모든 키워드가 0회`() {
        val result = RagQualityComparisonIT.countByKeyword("")
        assertThat(result.values).allMatch { it == 0 }
        assertThat(RagQualityComparisonIT.countKeywords("")).isEqualTo(0)
    }

    @Test
    fun `여러 키워드가 섞이면 각각 집계되고 합계가 맞는다`() {
        val text = "UseCase와 Default 그리고 헥사고날 구조를 지켜야 합니다"
        val result = RagQualityComparisonIT.countByKeyword(text)
        assertThat(result["UseCase"]).isEqualTo(1)
        assertThat(result["Default"]).isEqualTo(1)
        assertThat(result["헥사고날"]).isEqualTo(1)
        assertThat(RagQualityComparisonIT.countKeywords(text)).isEqualTo(3)
    }
}
