package stillframe42.aicodereviewer.rag.integration

import java.io.File
import java.time.LocalDateTime
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

// RAG 컨텍스트 베이스라인 측정 — 컨텍스트 압축 전 baseline 수집
// 일반 빌드에서는 자동 스킵. 수동 실행:
//   RAG_MANUAL_TEST=true ./gradlew test --tests "*RagContextMeasurementIT*"
// 공통 측정 인프라(SAMPLE_QUERIES, measureChunks 등)는 RagMeasurementSupport.kt에 정의
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "RAG_MANUAL_TEST", matches = "true")
class RagContextMeasurementIT {

    companion object {
        // AbstractIntegrationTest의 Singleton 컨테이너 재사용 — 새 컨테이너 기동 없이 기존 인스턴스 공유
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
            // spring.ai.openai.base-url 미설정 → application-ai.yml 기본값(실제 OpenAI API) 사용
            // 이유: WireMock mock 임베딩은 모든 벡터가 동일하여 HNSW 검색이 0건 반환
            val secrets = readSecrets()
            secrets["openai"]?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
        }

        // application-secret.yml에서 OpenAI API 키를 읽어 반환
        // (Anthropic 키는 본 측정에서 불필요 — 임베딩만 사용)
        internal fun readSecrets(): Map<String, String> = runCatching {
            val file = File("src/main/resources/application-secret.yml")
            check(file.exists()) { "application-secret.yml 파일을 찾을 수 없습니다" }
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(file.inputStream())
            buildMap {
                (map["openai"] as? Map<*, *>)?.get("api-key")?.let { put("openai", it as String) }
            }
        }.getOrElse { e ->
            System.err.println("[RagContextMeasurementIT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }

        // QueryMeasurement 리스트로부터 마크다운 형식 baseline 보고서 문자열 생성
        internal fun formatBaselineReport(measurements: List<QueryMeasurement>): String = buildString {
            appendLine("# RAG 컨텍스트 압축 실험 — 베이스라인 측정")
            appendLine()
            appendMetadata()
            appendSummaryStats(measurements)
            appendQueryDetails(measurements)
            appendQualitativeObservation(measurements)
            appendPhase4ComparisonTable(measurements)
        }

        private fun StringBuilder.appendMetadata() {
            appendLine("## 메타데이터")
            appendLine("- 측정 일시: ${LocalDateTime.now()}")
            appendLine("- 토큰 카운터: JTokkitTokenCountEstimator (cl100k_base)")
            appendLine("- top-K: 5")
            appendLine("- 압축 목표: 30% 이상 토큰 절감 (Phase 4에서 검증)")
            appendLine()
        }

        private fun StringBuilder.appendSummaryStats(measurements: List<QueryMeasurement>) {
            appendLine("## 요약 통계")
            val avgJoined = measurements.map { it.joinedTokens }.average().toInt()
            val avgSum = measurements.map { it.totalTokens }.average().toInt()
            appendLine("- 전체 평균 (쿼리당): joined $avgJoined tokens, sum-of-chunks $avgSum tokens")
            appendLine()
            appendLine("### 카테고리별 평균")
            appendLine("| 카테고리 | 평균 joined | 평균 sum | 평균 청크 토큰 |")
            appendLine("|---------|-----------|--------|------------|")
            measurements.groupBy { it.query.expectedCategory }.toSortedMap().forEach { (cat, ms) ->
                val cj = ms.map { it.joinedTokens }.average().toInt()
                val cs = ms.map { it.totalTokens }.average().toInt()
                val cc = ms.flatMap { it.chunks }.map { it.tokens }.average().toInt()
                appendLine("| $cat | $cj | $cs | $cc |")
            }
            appendLine()
            val allChunks = measurements.flatMap { it.chunks }.map { it.tokens }
            if (allChunks.isNotEmpty()) {
                appendLine("### 청크별 분포")
                appendLine("- 평균: ${allChunks.average().toInt()} tokens")
                appendLine("- 최소 / 최대: ${allChunks.min()} / ${allChunks.max()}")
                appendLine()
            }
        }

        private fun StringBuilder.appendQueryDetails(measurements: List<QueryMeasurement>) {
            appendLine("## 쿼리별 상세")
            appendLine()
            measurements.forEach { m ->
                appendLine("### ${m.query.id} — ${m.query.queryText} (${m.query.expectedCategory})")
                appendLine("- joined: ${m.joinedTokens} tokens")
                appendLine("- sum-of-chunks: ${m.totalTokens} tokens")
                appendLine()
                appendLine("| rank | tokens | chars | source |")
                appendLine("|------|--------|-------|--------|")
                m.chunks.forEach { c ->
                    appendLine("| ${c.rank} | ${c.tokens} | ${c.chars} | ${c.sourceFile ?: "-"} |")
                }
                appendLine()
                appendLine("#### 청크 원문 (정성 분석용)")
                appendLine()
                m.chunks.forEach { c ->
                    appendLine("**[${m.query.id}] Chunk #${c.rank} (${c.tokens} tokens)**")
                    appendLine()
                    c.text.lines().forEach { line -> appendLine("> $line") }
                    appendLine()
                }
            }
        }

        private fun StringBuilder.appendQualitativeObservation(measurements: List<QueryMeasurement>) {
            appendLine("## 정성 관찰")
            appendLine()
            appendLine("> 이 섹션은 측정 후 사람이 청크 원문을 훑어보고 직접 작성한다.")
            appendLine("> 자동 생성되지 않으며, 측정 직후 빈 항목으로 남는다.")
            appendLine()
            measurements.forEach { m ->
                appendLine("- ${m.query.id}: (관찰 메모를 여기에 작성)")
            }
            appendLine()
        }

        private fun StringBuilder.appendPhase4ComparisonTable(measurements: List<QueryMeasurement>) {
            appendLine("## Phase 4 비교용 베이스라인 표")
            appendLine()
            appendLine("> 압축 후 동일 측정을 재실행하여 \"압축 후\" 컬럼을 채운다.")
            appendLine()
            appendLine("| 쿼리 ID | 압축 전 joined tokens | 압축 후 | 절감률 |")
            appendLine("|---------|----------------------|--------|------|")
            measurements.forEach { m ->
                appendLine("| ${m.query.id} | ${m.joinedTokens} | - | - |")
            }
        }
    }

    @Autowired
    private lateinit var hybridSearchService: HybridConventionSearchService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `RAG 컨텍스트 토큰 베이스라인 측정`() {
        val secrets = readSecrets()
        Assumptions.assumeTrue(secrets.containsKey("openai")) {
            "application-secret.yml의 openai.api-key가 필요합니다 (실제 임베딩 호출용)"
        }

        // 1. vector_store 초기화 후 재인덱싱
        jdbcTemplate.execute("DELETE FROM vector_store")
        conventionIndexUseCase.reindex()

        // 2. 사전 정의된 8개 쿼리 순회 — 카테고리 매핑 무결성 검증 + 측정
        val measurements = SAMPLE_QUERIES.map { query ->
            val actualCategory = FileCategoryMapper.selectCategory(query.filePath)
            check(actualCategory == query.expectedCategory) {
                "${query.id}: 카테고리 매핑 불일치 — 기대 ${query.expectedCategory}, 실제 $actualCategory"
            }
            val docs = hybridSearchService.searchRaw(
                query = query.queryText,
                topK = 5,
                category = query.expectedCategory,
            )
            check(docs.isNotEmpty()) {
                "${query.id}: 검색 결과가 0건입니다 — vector_store 또는 카테고리 매핑 확인"
            }
            measureChunks(query, docs)
        }

        // 3. 보고서 생성 + 저장
        val reportPath = "plans/202604-2w/compression-experiment.md"
        val file = File(reportPath)
        file.parentFile?.mkdirs()
        file.writeText(formatBaselineReport(measurements))

        println("[RagContextMeasurementIT] 베이스라인 보고서 생성: $reportPath")
        println("[RagContextMeasurementIT] 평균 joined 토큰: ${measurements.map { it.joinedTokens }.average().toInt()}")
    }
}
