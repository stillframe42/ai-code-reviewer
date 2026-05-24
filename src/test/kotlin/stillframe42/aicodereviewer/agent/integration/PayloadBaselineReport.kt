package stillframe42.aicodereviewer.agent.integration

// AgentAnalysisRequest payload 의 4구간 분해 측정값
// meta = totalBytes - diffBytes - ragContextBytes — JSON 구조·키·escape·기타 필드 모두 포함
data class Measurement(
    val sampleId: String,
    val totalBytes: Int,
    val diffBytes: Int,
    val ragContextBytes: Int,
    val metaBytes: Int,
    val ragChunkCount: Int,
    val ragJoinedLen: Int,
)

object PayloadBaselineReport {

    fun format(
        measurements: List<Measurement>,
        timestamp: String,
        commit: String,
        rawSnapshotSampleId: String,
        rawSnapshotBody: String,
    ): String {
        require(measurements.isNotEmpty()) { "measurements must not be empty" }
        return buildString {
            appendMetadata(timestamp, commit)
            appendPerSampleTable(measurements)
            appendRatioSection(measurements)
            appendPhase5ComparisonTable(measurements)
            appendRawSnapshot(rawSnapshotSampleId, rawSnapshotBody)
        }
    }

    private fun StringBuilder.appendMetadata(timestamp: String, commit: String) {
        appendLine("# DAY 17 Phase 1 — AgentAnalysisRequest 페이로드 baseline")
        appendLine()
        appendLine("## 메타데이터")
        appendLine("- 측정 일시: $timestamp")
        appendLine("- 측정 커밋: $commit")
        appendLine("- 측정 방식: WireMock 통합 테스트 (AgentReviewUseCase end-to-end)")
        appendLine("- vector_store: ConventionIndexUseCase.reindex() 직후")
        appendLine()
    }

    private fun StringBuilder.appendPerSampleTable(ms: List<Measurement>) {
        appendLine("## 샘플별 측정값")
        appendLine("| 샘플 | total | diff | ragContext | meta | rag chunks |")
        appendLine("|------|------:|-----:|-----------:|-----:|-----------:|")
        ms.forEach {
            appendLine("| ${it.sampleId} | ${it.totalBytes} | ${it.diffBytes} | ${it.ragContextBytes} | ${it.metaBytes} | ${it.ragChunkCount} |")
        }
        val avg = ms.averaged()
        appendLine("| **평균** | ${avg.totalBytes} | ${avg.diffBytes} | ${avg.ragContextBytes} | ${avg.metaBytes} | ${avg.ragChunkCount} |")
        val max = ms.maxed()
        appendLine("| **최대** | ${max.totalBytes} | ${max.diffBytes} | ${max.ragContextBytes} | ${max.metaBytes} | ${max.ragChunkCount} |")
        appendLine()
    }

    private fun StringBuilder.appendRatioSection(ms: List<Measurement>) {
        appendLine("## 영역별 비율 (평균 기준)")
        val avg = ms.averaged()
        val total = avg.totalBytes.coerceAtLeast(1)
        appendLine("- diff: ${pct(avg.diffBytes, total)}%")
        appendLine("- ragContext: ${pct(avg.ragContextBytes, total)}%   ← Phase 5 의 50% 감소 목표 적용 영역")
        appendLine("- meta: ${pct(avg.metaBytes, total)}%")
        appendLine()
    }

    private fun StringBuilder.appendPhase5ComparisonTable(ms: List<Measurement>) {
        appendLine("## Phase 5 비교 결과")
        appendLine()
        appendLine("> Baseline (before) 은 Phase 1 측정 시점의 freeze 값. after 는 IT 재실행 시점 측정값.")
        appendLine()
        appendLine("| 샘플 | before total | after total | 감소율 | before ragContext | after ragContext | 감소율 |")
        appendLine("|------|-------------:|------------:|------:|------------------:|-----------------:|------:|")
        ms.forEach {
            val before = Baseline.SAMPLES[it.sampleId]
            if (before != null) {
                appendLine(
                    "| ${it.sampleId} | ${before.totalBytes} | ${it.totalBytes} | ${reductionPct(before.totalBytes, it.totalBytes)}% " +
                        "| ${before.ragContextBytes} | ${it.ragContextBytes} | ${reductionPct(before.ragContextBytes, it.ragContextBytes)}% |",
                )
            } else {
                appendLine("| ${it.sampleId} | - | ${it.totalBytes} | - | - | ${it.ragContextBytes} | - |")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendRawSnapshot(sampleId: String, body: String) {
        appendLine("## raw payload snapshot ($sampleId)")
        appendLine()
        appendLine("```json")
        appendLine(body)
        appendLine("```")
    }

    private fun pct(part: Int, total: Int): Int = (part.toLong() * 100 / total).toInt()
}

// 평균 계산 — Int 평균이라 소수점은 버린다 (보고서 가독성 우선)
private fun List<Measurement>.averaged(): Measurement = Measurement(
    sampleId = "avg",
    totalBytes = map { it.totalBytes }.average().toInt(),
    diffBytes = map { it.diffBytes }.average().toInt(),
    ragContextBytes = map { it.ragContextBytes }.average().toInt(),
    metaBytes = map { it.metaBytes }.average().toInt(),
    ragChunkCount = map { it.ragChunkCount }.average().toInt(),
    ragJoinedLen = map { it.ragJoinedLen }.average().toInt(),
)

private fun List<Measurement>.maxed(): Measurement = Measurement(
    sampleId = "max",
    totalBytes = maxOf { it.totalBytes },
    diffBytes = maxOf { it.diffBytes },
    ragContextBytes = maxOf { it.ragContextBytes },
    metaBytes = maxOf { it.metaBytes },
    ragChunkCount = maxOf { it.ragChunkCount },
    ragJoinedLen = maxOf { it.ragJoinedLen },
)

// Phase 1 측정 시점의 freeze 값 — Phase 5 비교표의 'before' 컬럼 소스
// 갱신 시 baseline-payload.md 와 동시에 의도 명시 커밋
private object Baseline {
    data class Entry(val totalBytes: Int, val ragContextBytes: Int)

    val SAMPLES: Map<String, Entry> = mapOf(
        "security-sql" to Entry(totalBytes = 5851, ragContextBytes = 4026),
        "arch-jpa"     to Entry(totalBytes = 2801, ragContextBytes = 1603),
        "style-long"   to Entry(totalBytes = 5884, ragContextBytes = 1603),
    )
}

private fun reductionPct(before: Int, after: Int): Int =
    if (before <= 0) 0 else (((before - after).toLong() * 100) / before).toInt()
