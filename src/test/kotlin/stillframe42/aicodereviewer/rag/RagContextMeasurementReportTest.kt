package stillframe42.aicodereviewer.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper

// RagContextMeasurementIT의 순수 함수(measureChunks, formatBaselineReport)를 단위 테스트한다.
// SpringBootTest 컨텍스트 없이 빠르게 실행되어 로직 변경 시 즉시 검증 가능
class RagContextMeasurementReportTest {

    @Test
    fun `SAMPLE_QUERIES 8개의 expectedCategory가 FileCategoryMapper와 일치한다`() {
        RagContextMeasurementIT.SAMPLE_QUERIES.forEach { query ->
            val actual = FileCategoryMapper.selectCategory(query.filePath)
            assertThat(actual)
                .withFailMessage(
                    "%s: expected=%s, actual=%s (filePath=%s)",
                    query.id, query.expectedCategory, actual, query.filePath,
                )
                .isEqualTo(query.expectedCategory)
        }
    }

    @Test
    fun `measureChunks는 청크별 토큰 수를 계산하고 joined와 sum을 분리한다`() {
        val docs = listOf(
            Document.builder()
                .text("첫 번째 청크 내용입니다. 헥사고날 아키텍처 규칙을 설명합니다.")
                .metadata(mapOf("source" to "architecture-guide.md"))
                .build(),
            Document.builder()
                .text("두 번째 청크 내용입니다. UseCase 인터페이스 규약입니다.")
                .metadata(mapOf("source" to "architecture-guide.md"))
                .build(),
        )
        val query = RagContextMeasurementIT.SampleQuery("test", "Test.kt", "Test.kt", ARCH)

        val result = RagContextMeasurementIT.measureChunks(query, docs)

        assertThat(result.chunks).hasSize(2)
        assertThat(result.chunks[0].rank).isEqualTo(1)
        assertThat(result.chunks[0].tokens).isGreaterThan(0)
        assertThat(result.chunks[0].sourceFile).isEqualTo("architecture-guide.md")
        assertThat(result.totalTokens).isEqualTo(result.chunks.sumOf { it.tokens })
        assertThat(result.joinedTokens).isGreaterThanOrEqualTo(result.totalTokens)  // 구분자 추가됨
    }

    @Test
    fun `measureChunks는 빈 docs에 대해 예외를 던진다`() {
        val query = RagContextMeasurementIT.SampleQuery("test", "Test.kt", "Test.kt", ARCH)
        try {
            RagContextMeasurementIT.measureChunks(query, emptyList())
            error("예외가 발생해야 함")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("test").contains("0건")
        }
    }
}
