package stillframe42.aicodereviewer.rag

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.document.Document
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.SECURITY
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.STYLE

// RAG 컨텍스트 베이스라인 측정 — 컨텍스트 압축(tasks_20260416.md Phase 2~5) 전 baseline 수집
// 일반 빌드에서는 자동 스킵. 수동 실행:
//   RAG_MANUAL_TEST=true ./gradlew test --tests "*RagContextMeasurementIT*"
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
        private fun readSecrets(): Map<String, String> = runCatching {
            val file = java.io.File("src/main/resources/application-secret.yml")
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

        // 측정 대상 쿼리 — 카테고리당 2개씩, FileCategoryMapper와 정확히 일치하도록 선정
        // 카테고리 매핑 우선순위: SECURITY > API > ARCH > STYLE
        val SAMPLE_QUERIES: List<SampleQuery> = listOf(
            // ARCH (헥사고날 아키텍처, 트랜잭션, N+1)
            SampleQuery("arch-1", "OrderService.kt",
                "src/main/kotlin/stillframe42/codereviewertester/order/application/OrderService.kt", ARCH),
            SampleQuery("arch-2", "ReviewRequestRepository.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/review/adapter/out/persistence/ReviewRequestRepository.kt", ARCH),

            // API (Controller, REST)
            SampleQuery("api-1", "OrderController.kt",
                "src/main/kotlin/stillframe42/codereviewertester/order/adapter/web/OrderController.kt", API),
            SampleQuery("api-2", "ChatController.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/chat/adapter/in/web/ChatController.kt", API),

            // STYLE (Kotlin 컨벤션) — 키워드 미매칭 파일명
            SampleQuery("style-1", "DiffPreprocessor.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/review/domain/service/DiffPreprocessor.kt", STYLE),
            SampleQuery("style-2", "ReviewMode.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/review/domain/model/ReviewMode.kt", STYLE),

            // SECURITY (인증, JWT)
            SampleQuery("sec-1", "JwtAuthenticationFilter.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/security/JwtAuthenticationFilter.kt", SECURITY),
            SampleQuery("sec-2", "SecurityConfig.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/config/SecurityConfig.kt", SECURITY),
        )

        // OpenAI cl100k_base 토크나이저 사용 — 임베딩 단계에서 사용되는 OpenAI text-embedding-3 기준
        // Anthropic 토크나이저는 JVM에서 직접 사용 가능한 라이브러리가 없어 cl100k_base로 근사한다.
        // 압축 전/후 비교에서는 동일 토크나이저를 사용하므로 절대값보다 상대 변화율이 의미 있다.
        private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator()

        // 텍스트의 토큰 수를 반환 (cl100k_base 기준)
        internal fun countTokens(text: String): Int = tokenEstimator.estimate(text)

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
            appendLine("- 측정 일시: ${java.time.LocalDateTime.now()}")
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

        // 검색된 Document 리스트로부터 청크별 + 종합 측정값 계산
        internal fun measureChunks(query: SampleQuery, docs: List<Document>): QueryMeasurement {
            require(docs.isNotEmpty()) {
                "${query.id}: 검색 결과가 0건입니다 — vector_store 또는 카테고리 매핑 확인"
            }
            val chunks = docs.mapIndexed { idx, doc ->
                val text = doc.text ?: ""
                ChunkMeasurement(
                    rank = idx + 1,
                    tokens = countTokens(text),
                    chars = text.length,
                    sourceFile = doc.metadata["source"] as? String,
                    text = text,
                )
            }
            val joined = docs.joinToString("\n\n---\n\n") { it.text ?: "" }
            return QueryMeasurement(
                query = query,
                chunks = chunks,
                totalTokens = chunks.sumOf { it.tokens },
                joinedTokens = countTokens(joined),
            )
        }
    }

    // 측정 대상 단일 쿼리 (카테고리, 파일 경로, 검색 텍스트)
    data class SampleQuery(
        val id: String,
        val queryText: String,
        val filePath: String,
        val expectedCategory: ConventionCategory,
    )

    // 청크 단위 측정 결과
    data class ChunkMeasurement(
        val rank: Int,
        val tokens: Int,
        val chars: Int,
        val sourceFile: String?,
        val text: String,
    )

    // 쿼리별 종합 측정 결과
    data class QueryMeasurement(
        val query: SampleQuery,
        val chunks: List<ChunkMeasurement>,
        val totalTokens: Int,    // 청크 토큰 합계 (구분자 제외)
        val joinedTokens: Int,   // join("\n\n---\n\n") 후 실제 프롬프트 주입 형태 토큰 수
    )

    @Test
    fun `스켈레톤 컴파일 검증 placeholder`() = runBlocking {
        // Task 5에서 실제 측정 로직으로 교체됨
        Unit
    }
}
