package stillframe42.aicodereviewer.rag.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.rag.domain.model.RagDocument

class ReciprocaRankFusionTest {

    private fun doc(id: String, content: String): RagDocument =
        RagDocument(id = id, text = content)

    @Test
    fun `양쪽 결과 모두 상위에 있는 문서가 최상위 순위를 받는다`() {
        // A: 벡터 1위 + 키워드 2위 → 양쪽 합산
        // C: 키워드 1위 + 벡터 없음
        // B: 벡터 2위 + 키워드 없음
        val vectorResults = listOf(doc("A", "OWASP Injection 방어"), doc("B", "입력값 검증 가이드"))
        val keywordResults = listOf(doc("C", "PreparedStatement 사용 규칙"), doc("A", "OWASP Injection 방어"))

        val result = reciprocalRankFusion(vectorResults, keywordResults, topK = 3)

        // A: 1/(60+1) + 1/(60+2) = 0.01639 + 0.01613 = 0.03252 (최상위)
        assertThat(result.first().id).isEqualTo("A")
        assertThat(result).hasSize(3)
    }

    @Test
    fun `topK만큼만 반환한다`() {
        val vectorResults = listOf(doc("A", "A"), doc("B", "B"), doc("C", "C"))
        val keywordResults = listOf(doc("D", "D"), doc("E", "E"), doc("F", "F"))

        val result = reciprocalRankFusion(vectorResults, keywordResults, topK = 2)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `전체 후보가 topK보다 적으면 전체를 반환한다`() {
        val vectorResults = listOf(doc("A", "A"))
        val keywordResults = listOf(doc("A", "A"))

        val result = reciprocalRankFusion(vectorResults, keywordResults, topK = 5)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `빈 결과가 입력되면 빈 결과를 반환한다`() {
        val result = reciprocalRankFusion(emptyList(), emptyList(), topK = 5)

        assertThat(result).isEmpty()
    }

    @Test
    fun `벡터 결과만 있으면 벡터 결과가 그대로 반환된다`() {
        val vectorResults = listOf(doc("A", "A"), doc("B", "B"))

        val result = reciprocalRankFusion(vectorResults, emptyList(), topK = 5)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder("A", "B")
    }

    @Test
    fun `키워드 결과만 있으면 키워드 결과가 그대로 반환된다`() {
        val keywordResults = listOf(doc("X", "X"), doc("Y", "Y"))

        val result = reciprocalRankFusion(emptyList(), keywordResults, topK = 5)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder("X", "Y")
    }
}
