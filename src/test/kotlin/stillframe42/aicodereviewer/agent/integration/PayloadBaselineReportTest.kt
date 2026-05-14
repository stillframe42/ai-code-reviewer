package stillframe42.aicodereviewer.agent.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PayloadBaselineReportTest {

    private val sampleMeasurements = listOf(
        Measurement(
            sampleId = "security-sql",
            totalBytes = 4000,
            diffBytes = 1500,
            ragContextBytes = 2300,
            metaBytes = 200,
            ragChunkCount = 1,
            ragJoinedLen = 2200,
        ),
        Measurement(
            sampleId = "arch-jpa",
            totalBytes = 3000,
            diffBytes = 1000,
            ragContextBytes = 1800,
            metaBytes = 200,
            ragChunkCount = 1,
            ragJoinedLen = 1750,
        ),
        Measurement(
            sampleId = "style-long",
            totalBytes = 5000,
            diffBytes = 2500,
            ragContextBytes = 2300,
            metaBytes = 200,
            ragChunkCount = 1,
            ragJoinedLen = 2250,
        ),
    )

    @Test
    fun `각 샘플 행이 테이블에 포함된다`() {
        val markdown = PayloadBaselineReport.format(
            measurements = sampleMeasurements,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = """{"pr_number":1}""",
        )

        assertThat(markdown).contains("| security-sql |")
        assertThat(markdown).contains("| arch-jpa |")
        assertThat(markdown).contains("| style-long |")
    }

    @Test
    fun `평균과 최대 행이 산출된다`() {
        val markdown = PayloadBaselineReport.format(
            measurements = sampleMeasurements,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = "{}",
        )

        // total 평균 (4000+3000+5000)/3 = 4000
        assertThat(markdown).contains("| **평균** | 4000 |")
        // total 최대 = 5000
        assertThat(markdown).contains("| **최대** | 5000 |")
    }

    @Test
    fun `메타데이터 섹션에 측정 일시와 커밋이 들어간다`() {
        val markdown = PayloadBaselineReport.format(
            measurements = sampleMeasurements,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = "{}",
        )

        assertThat(markdown).contains("측정 일시: 2026-05-14T10:00:00")
        assertThat(markdown).contains("측정 커밋: abc1234")
    }

    @Test
    fun `Phase 5 비교 결과 표는 Baseline 매칭 샘플의 before·after·감소율을 채운다`() {
        // Baseline.SAMPLES["security-sql"] = (5851, 4026)
        // sampleMeasurements 의 security-sql 은 totalBytes=4000, ragContextBytes=2300
        // total 감소율 = (5851 - 4000) / 5851 ≈ 31%
        // ragContext 감소율 = (4026 - 2300) / 4026 ≈ 42%
        val markdown = PayloadBaselineReport.format(
            measurements = sampleMeasurements,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = "{}",
        )

        assertThat(markdown).contains("## Phase 5 비교 결과")
        assertThat(markdown).contains("| security-sql | 5851 | 4000 | 31% | 4026 | 2300 | 42% |")
    }

    @Test
    fun `Baseline 에 없는 sampleId 는 before 컬럼이 dash 로 fallback`() {
        val unknown = listOf(
            Measurement(
                sampleId = "unknown-sample",
                totalBytes = 1234,
                diffBytes = 100,
                ragContextBytes = 500,
                metaBytes = 634,
                ragChunkCount = 1,
                ragJoinedLen = 480,
            ),
        )
        val markdown = PayloadBaselineReport.format(
            measurements = unknown,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "unknown-sample",
            rawSnapshotBody = "{}",
        )

        assertThat(markdown).contains("| unknown-sample | - | 1234 | - | - | 500 | - |")
    }

    @Test
    fun `Phase 5 비교 결과 표 머리말이 before·after·감소율 컬럼을 명시한다`() {
        val markdown = PayloadBaselineReport.format(
            measurements = sampleMeasurements,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = "{}",
        )

        assertThat(markdown).contains("| 샘플 | before total | after total | 감소율 | before ragContext | after ragContext | 감소율 |")
    }

    @Test
    fun `영역별 비율은 평균 기준으로 정확히 계산된다`() {
        val markdown = PayloadBaselineReport.format(
            measurements = sampleMeasurements,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = "{}",
        )

        // 평균: total=4000, diff=1666, ragContext=2133, meta=200
        // diff/total = 1666/4000 = 41%
        // ragContext/total = 2133/4000 = 53%
        // meta/total = 200/4000 = 5%
        assertThat(markdown).contains("- diff: 41%")
        assertThat(markdown).contains("- ragContext: 53%")
        assertThat(markdown).contains("- meta: 5%")
    }

    @Test
    fun `raw payload snapshot 섹션에 지정된 본문이 포함된다`() {
        val markdown = PayloadBaselineReport.format(
            measurements = sampleMeasurements,
            timestamp = "2026-05-14T10:00:00",
            commit = "abc1234",
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = """{"pr_number":1,"diff":"sample"}""",
        )

        assertThat(markdown).contains("raw payload snapshot (security-sql")
        assertThat(markdown).contains(""""pr_number":1,"diff":"sample"""")
    }
}
