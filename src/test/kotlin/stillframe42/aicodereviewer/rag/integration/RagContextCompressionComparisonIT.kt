package stillframe42.aicodereviewer.rag.integration

import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.QueryMeasurement
import stillframe42.aicodereviewer.rag.SAMPLE_QUERIES
import stillframe42.aicodereviewer.rag.SampleQuery
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper
import stillframe42.aicodereviewer.rag.measureChunks

// RAG 컨텍스트 압축 전/후 비교 측정 — Phase 4
// 일반 빌드에서는 자동 스킵. 수동 실행:
//   RAG_MANUAL_TEST=true ./gradlew test --tests "*RagContextCompressionComparisonIT*"
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "RAG_MANUAL_TEST", matches = "true")
class RagContextCompressionComparisonIT {

    companion object {
        // AbstractIntegrationTest의 Singleton 컨테이너 재사용
        val wireMock = AbstractIntegrationTest.wireMock
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("github.api.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("langfuse.host") { "http://localhost:${wireMock.port()}" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            // spring.ai.openai.base-url 미설정 → 실제 OpenAI API (임베딩 + 압축 모두)
            val secrets = readSecrets()
            secrets["openai"]?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
        }

        // application-secret.yml에서 OpenAI 키 로드 (검색 임베딩 + 압축 LLM 모두 OpenAI 사용)
        internal fun readSecrets(): Map<String, String> = runCatching {
            val file = File("src/main/resources/application-secret.yml")
            check(file.exists()) { "application-secret.yml 파일을 찾을 수 없습니다" }
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(file.inputStream())
            buildMap {
                (map["openai"] as? Map<*, *>)?.get("api-key")?.let { put("openai", it as String) }
            }
        }.getOrElse { e ->
            System.err.println("[RagContextCompressionComparisonIT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }
    }

    // 압축 전/후 비교 결과
    internal data class ComparisonResult(
        val query: SampleQuery,
        val rawMeasurement: QueryMeasurement,
        val compressedMeasurement: QueryMeasurement,
    ) {
        val reductionRate: Double
            get() = if (rawMeasurement.joinedTokens == 0) 0.0
            else (rawMeasurement.joinedTokens - compressedMeasurement.joinedTokens).toDouble() /
                rawMeasurement.joinedTokens
        val rawChunkCount: Int get() = rawMeasurement.chunks.size
        val compressedChunkCount: Int get() = compressedMeasurement.chunks.size
    }

    @Autowired
    private lateinit var hybridSearchService: HybridConventionSearchService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `RAG 컨텍스트 압축 전후 비교 측정`() = runBlocking {
        val secrets = readSecrets()
        Assumptions.assumeTrue(secrets.containsKey("openai")) {
            "application-secret.yml의 openai.api-key가 필요합니다 (검색 + 압축 모두 OpenAI 사용)"
        }

        // 1. vector_store 초기화 + reindex
        jdbcTemplate.execute("DELETE FROM vector_store")
        conventionIndexUseCase.reindex()

        // 2. 8개 쿼리에 대해 raw + compressed 동시 측정
        val comparisons = SAMPLE_QUERIES.map { query ->
            val actualCategory = FileCategoryMapper.selectCategory(query.filePath)
            check(actualCategory == query.expectedCategory) {
                "${query.id}: 카테고리 매핑 불일치 — 기대 ${query.expectedCategory}, 실제 $actualCategory"
            }
            val rawDocs = hybridSearchService.searchRaw(query.queryText, 5, query.expectedCategory)
            val compressedDocs = hybridSearchService.search(query.queryText, 5, query.expectedCategory)
            check(rawDocs.isNotEmpty()) {
                "${query.id}: raw 검색 결과가 0건입니다"
            }
            ComparisonResult(
                query = query,
                rawMeasurement = measureChunks(query, rawDocs),
                compressedMeasurement = measureChunks(query, compressedDocs),
            )
        }

        // 3. 비교 보고서 생성
        val reportPath = "plans/202604-2w/compression-comparison.md"
        val file = File(reportPath)
        file.parentFile?.mkdirs()
        file.writeText(formatComparisonReport(comparisons))

        val avgReduction = comparisons.map { it.reductionRate }.average()
        println("[비교 측정] 보고서 생성: $reportPath")
        println("[비교 측정] 평균 절감률: ${(avgReduction * 100).toInt()}%")
    }

    // 비교 결과를 마크다운 보고서로 포맷팅
    private fun formatComparisonReport(comparisons: List<ComparisonResult>): String = buildString {
        appendLine("# RAG 컨텍스트 압축 전/후 비교")
        appendLine()
        appendMetadata()
        appendSummaryStats(comparisons)
        appendQueryComparisonTable(comparisons)
        appendQualityWarnings(comparisons)
        appendQueryDetails(comparisons)
    }

    private fun StringBuilder.appendMetadata() {
        appendLine("## 메타데이터")
        appendLine("- 측정 일시: ${LocalDateTime.now()}")
        appendLine("- 압축 모델: gpt-4o-mini")
        appendLine("- 압축 임계값: 300 tokens")
        appendLine("- top-K: 5")
        appendLine("- 압축 목표: 30% 이상 토큰 절감 (Phase 1에서 설정)")
        appendLine()
    }

    private fun StringBuilder.appendSummaryStats(comparisons: List<ComparisonResult>) {
        // 토큰 총합 기준 절감률 — 실제 비용/품질에 직결되는 핵심 지표 (목표 달성 판정의 기준)
        val totalRaw = comparisons.sumOf { it.rawMeasurement.joinedTokens }
        val totalCompressed = comparisons.sumOf { it.compressedMeasurement.joinedTokens }
        val totalReductionPercent = if (totalRaw == 0) 0 else ((totalRaw - totalCompressed) * 100 / totalRaw)
        // 단순 평균 절감률 — 쿼리별 절감률의 산술 평균 (참고용, STYLE 우회 영향이 분모에 포함됨)
        val avgReductionPercent = (comparisons.map { it.reductionRate }.average() * 100).toInt()
        val targetMet = totalReductionPercent >= 30

        appendLine("## 요약 통계")
        appendLine("- **토큰 총합 기준 절감률: ${totalReductionPercent}%** ${if (targetMet) "✅" else "❌"} (목표 30% 기준 달성 여부 — 핵심 지표)")
        appendLine("- 단순 평균 절감률: ${avgReductionPercent}% (쿼리별 절감률의 산술 평균 — STYLE 우회 영향 포함)")
        appendLine("- 압축 전 토큰 총합: $totalRaw / 압축 후 토큰 총합: $totalCompressed (절감 ${totalRaw - totalCompressed} tokens)")
        appendLine()
        appendLine("### 카테고리별 평균 절감률")
        appendLine("| 카테고리 | 평균 압축 전 | 평균 압축 후 | 절감률 |")
        appendLine("|---------|-----------|-----------|------|")
        comparisons.groupBy { it.query.expectedCategory }.toSortedMap().forEach { (cat, items) ->
            val avgRaw = items.map { it.rawMeasurement.joinedTokens }.average().toInt()
            val avgCompressed = items.map { it.compressedMeasurement.joinedTokens }.average().toInt()
            val rate = if (avgRaw == 0) 0 else ((avgRaw - avgCompressed) * 100 / avgRaw)
            appendLine("| $cat | $avgRaw | $avgCompressed | ${rate}% |")
        }
        appendLine()
    }

    private fun StringBuilder.appendQueryComparisonTable(comparisons: List<ComparisonResult>) {
        appendLine("## 쿼리별 비교 표")
        appendLine("| 쿼리 ID | 카테고리 | 압축 전 joined | 압축 후 joined | 절감 토큰 | 절감률 | 청크 수(전→후) |")
        appendLine("|---------|---------|--------------|--------------|---------|--------|--------------|")
        comparisons.forEach { c ->
            val rawTokens = c.rawMeasurement.joinedTokens
            val compTokens = c.compressedMeasurement.joinedTokens
            val saved = rawTokens - compTokens
            val ratePercent = (c.reductionRate * 100).toInt()
            appendLine("| ${c.query.id} | ${c.query.expectedCategory} | $rawTokens | $compTokens | $saved | ${ratePercent}% | ${c.rawChunkCount} → ${c.compressedChunkCount} |")
        }
        appendLine()
    }

    private fun StringBuilder.appendQualityWarnings(comparisons: List<ComparisonResult>) {
        val warnings = comparisons.filter {
            it.reductionRate >= 0.7 || (it.rawChunkCount > 0 && it.compressedChunkCount * 2 < it.rawChunkCount)
        }
        appendLine("## 품질 저하 의심 케이스 (사람 검토 필요)")
        appendLine()
        appendLine("> 절감률이 70% 이상이거나 청크 수가 절반 이하로 줄어든 케이스를 자동 표시.")
        appendLine("> 사람이 압축 결과 원문을 확인하여 핵심 정보 손실 여부를 판정.")
        appendLine()
        if (warnings.isEmpty()) {
            appendLine("- 의심 케이스 없음 ✅")
        } else {
            warnings.forEach { c ->
                val ratePercent = (c.reductionRate * 100).toInt()
                appendLine("- **${c.query.id}**: ${ratePercent}% 절감, 청크 ${c.rawChunkCount} → ${c.compressedChunkCount}")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendQueryDetails(comparisons: List<ComparisonResult>) {
        appendLine("## 쿼리별 상세")
        appendLine()
        comparisons.forEach { c ->
            appendLine("### ${c.query.id} — ${c.query.queryText} (${c.query.expectedCategory})")
            appendLine()
            appendLine("**압축 전 (raw)** — joined: ${c.rawMeasurement.joinedTokens} tokens, 청크 ${c.rawChunkCount}개")
            c.rawMeasurement.chunks.forEach { chunk ->
                val preview = chunk.text.take(120).replace("\n", " ")
                appendLine("- Chunk #${chunk.rank} (${chunk.tokens} tokens, source: ${chunk.sourceFile ?: "-"}): $preview...")
            }
            appendLine()
            appendLine("**압축 후 (compressed)** — joined: ${c.compressedMeasurement.joinedTokens} tokens, 청크 ${c.compressedChunkCount}개")
            if (c.compressedMeasurement.chunks.isEmpty()) {
                appendLine("- (모든 청크가 압축으로 제외됨)")
            } else {
                c.compressedMeasurement.chunks.forEach { chunk ->
                    val preview = chunk.text.take(120).replace("\n", " ")
                    appendLine("- Chunk #${chunk.rank} (${chunk.tokens} tokens, source: ${chunk.sourceFile ?: "-"}): $preview...")
                }
            }
            appendLine()
        }
    }
}
