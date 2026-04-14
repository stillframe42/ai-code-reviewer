package stillframe42.aicodereviewer.rag

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
        assertThatThrownBy {
            RagContextMeasurementIT.measureChunks(query, emptyList())
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("test")
            .hasMessageContaining("0건")
    }

    @Test
    fun `formatBaselineReport는 메타데이터 요약 상세 정성관찰 Phase4표 5개 섹션을 포함한다`() {
        val query = RagContextMeasurementIT.SampleQuery(
            "arch-1", "OrderService.kt", "src/main/.../OrderService.kt", ARCH,
        )
        val measurement = RagContextMeasurementIT.QueryMeasurement(
            query = query,
            chunks = listOf(
                RagContextMeasurementIT.ChunkMeasurement(1, 100, 400, "architecture-guide.md", "샘플 청크 텍스트"),
            ),
            totalTokens = 100,
            joinedTokens = 100,
        )
        val report = RagContextMeasurementIT.formatBaselineReport(listOf(measurement))

        assertThat(report).contains("# RAG 컨텍스트 압축 실험")
        assertThat(report).contains("## 메타데이터")
        assertThat(report).contains("## 요약 통계")
        assertThat(report).contains("## 쿼리별 상세")
        assertThat(report).contains("### arch-1")
        assertThat(report).contains("샘플 청크 텍스트")
        assertThat(report).contains("architecture-guide.md")
        assertThat(report).contains("## 정성 관찰")
        assertThat(report).contains("## Phase 4 비교용 베이스라인 표")
        assertThat(report).contains("| arch-1 |")
    }

    @Test
    fun `formatBaselineReport 요약 통계가 카테고리별 평균을 포함한다`() {
        val measurements = listOf(
            makeMeasurement("arch-1", "OrderService.kt", "OrderService.kt", ARCH, joined = 200),
            makeMeasurement("arch-2", "Foo.kt", "Foo.kt", ARCH, joined = 400),
        )
        val report = RagContextMeasurementIT.formatBaselineReport(measurements)

        assertThat(report).contains("ARCH")
        // 평균이 (200 + 400) / 2 = 300 으로 계산되어야 함
        assertThat(report).contains("300")
    }

    private fun makeMeasurement(
        id: String,
        queryText: String,
        filePath: String,
        category: stillframe42.aicodereviewer.rag.domain.model.ConventionCategory,
        joined: Int,
    ): RagContextMeasurementIT.QueryMeasurement {
        val query = RagContextMeasurementIT.SampleQuery(id, queryText, filePath, category)
        return RagContextMeasurementIT.QueryMeasurement(
            query = query,
            chunks = listOf(RagContextMeasurementIT.ChunkMeasurement(1, joined, joined * 4, "src.md", "내용")),
            totalTokens = joined,
            joinedTokens = joined,
        )
    }
}
