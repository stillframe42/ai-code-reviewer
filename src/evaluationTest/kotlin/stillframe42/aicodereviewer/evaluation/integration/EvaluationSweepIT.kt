package stillframe42.aicodereviewer.evaluation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationMetric
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase
import stillframe42.aicodereviewer.evaluation.domain.port.`in`.EvaluationUseCase
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import java.io.File
import java.time.Instant

// TopK / Threshold sweep 측정 — 실 API 호출 (비용·시간 큼)
// EVAL_MANUAL_TEST=true ./gradlew test --tests "*EvaluationSweepIT*"
// 결과는 plans/202604-3w/sweep-results/*.json 에 incremental write.
// styleFilterBypass=true 고정 — STYLE 신호 확보용.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class EvaluationSweepIT {

    companion object {
        val wireMock = AbstractIntegrationTest.wireMock
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        // TopK sweep 결과 채택값 — Threshold sweep 에서 이 값 고정
        const val OPTIMAL_TOPK_FROM_B1 = 3

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
            // STYLE 신호 확보를 위해 카테고리 필터 우회 고정
            registry.add("app.rag.style-filter-bypass") { "true" }
            // spring.ai.*.base-url 미설정 → 실제 API 엔드포인트 사용

            val secrets = readSecrets()
            secrets["openai"]?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
            secrets["anthropic"]?.let { key ->
                registry.add("anthropic.api-key") { key }
                registry.add("spring.ai.anthropic.api-key") { key }
            }
        }

        private fun readSecrets(): Map<String, String> = runCatching {
            val file = File("src/main/resources/application-secret.yml")
            check(file.exists()) { "application-secret.yml 파일을 찾을 수 없습니다" }
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(file.inputStream())
            buildMap {
                (map["openai"] as? Map<*, *>)?.get("api-key")?.let { put("openai", it as String) }
                (map["anthropic"] as? Map<*, *>)?.get("api-key")?.let { put("anthropic", it as String) }
            }
        }.getOrElse { e ->
            System.err.println("[EvaluationSweepIT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }
    }

    @Autowired
    private lateinit var evaluationUseCase: EvaluationUseCase

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val sweepResultsDir = File("plans/202604-3w/sweep-results").also { it.mkdirs() }

    // 골든 데이터셋 로드 — 20개 케이스
    protected fun loadGoldenCases(): List<GoldenCase> = GoldenDatasetLoader.load()

    // 단일 sweep run 결과를 JSON 파일에 incremental append
    protected fun writeRunResult(
        sweepFile: String,
        runLabel: String,
        params: Map<String, Any>,
        results: List<EvaluationResult>,
    ) {
        val file = File(sweepResultsDir, sweepFile)
        val existing: MutableMap<String, Any> = if (file.exists()) {
            objectMapper.readValue(file)
        } else {
            mutableMapOf("experimentId" to sweepFile.removeSuffix(".json"), "runs" to mutableListOf<Any>())
        }
        @Suppress("UNCHECKED_CAST")
        val runs = existing["runs"] as MutableList<Map<String, Any>>

        val avgScores = EvaluationMetric.entries.associate { metric ->
            metric.name to results.mapNotNull { r -> r.scores.firstOrNull { it.metric == metric }?.score }
                .takeIf { it.isNotEmpty() }?.average()
        }
        val categoryScores = listOf("SEC", "ARCH", "STYLE", "API").associateWith { prefix ->
            val cat = results.filter { it.caseId.startsWith(prefix) }
            EvaluationMetric.entries.associate { metric ->
                metric.name to cat.mapNotNull { r -> r.scores.firstOrNull { it.metric == metric }?.score }
                    .takeIf { it.isNotEmpty() }?.average()
            }
        }
        val tokenAvg = results.map { it.contextTokenEstimate }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0

        runs += mapOf(
            "label" to runLabel,
            "params" to params,
            "avgScores" to avgScores,
            "categoryScores" to categoryScores,
            "contextTokenAvg" to tokenAvg,
            "perCase" to results.map { r ->
                mapOf(
                    "caseId" to r.caseId,
                    "tokenEstimate" to r.contextTokenEstimate,
                    "scores" to r.scores.associate { it.metric.name to it.score },
                )
            },
            "executedAt" to Instant.now().toString(),
        )

        file.writeText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(existing))
        println("\n[Sweep] ${sweepFile}에 run '$runLabel' 추가 완료 (avg: $avgScores)")
    }

    // 골격 검증 — 인덱싱 + 골든 데이터셋 로드 (LLM 호출 없음, 빠름)
    @Test
    fun `sweep 골격 검증`() = runBlocking {
        conventionIndexUseCase.reindex()
        val cases = loadGoldenCases()
        check(cases.size == 20) { "골든 데이터셋 케이스가 20개여야 합니다 (실제: ${cases.size})" }
        println("[Sweep] 인덱싱 + 골든 데이터셋 ${cases.size}개 로드 확인")
    }

    // TopK Sweep — TopK ∈ {3, 5, 7}, threshold=0.0 고정. 비용/시간: ~24분, ~$3-6.
    @Test
    fun `B-1 TopK Sweep`() = runBlocking {
        conventionIndexUseCase.reindex()
        val cases = loadGoldenCases()

        listOf(3, 5, 7).forEach { topK ->
            println("\n=== TopK Sweep: topK=$topK ===")
            val results = evaluationUseCase.evaluateAll(cases, topK = topK, threshold = 0.0)
            writeRunResult(
                sweepFile = "topk-sweep.json",
                runLabel = "topK=$topK",
                params = mapOf("topK" to topK, "threshold" to 0.0),
                results = results,
            )
        }
    }

    // Threshold Sweep — Threshold ∈ {0.5, 0.6, 0.7, 0.8}, TopK=OPTIMAL_TOPK_FROM_B1 고정
    // 비용/시간: ~32분, ~$4-8 (4회 × 8분, $1-2)
    @Test
    fun `B-2 Threshold Sweep`() = runBlocking {
        conventionIndexUseCase.reindex()
        val cases = loadGoldenCases()

        listOf(0.5, 0.6, 0.7, 0.8).forEach { threshold ->
            println("\n=== Threshold Sweep: threshold=$threshold (topK=$OPTIMAL_TOPK_FROM_B1) ===")
            val results = evaluationUseCase.evaluateAll(
                cases,
                topK = OPTIMAL_TOPK_FROM_B1,
                threshold = threshold,
            )
            writeRunResult(
                sweepFile = "threshold-sweep.json",
                runLabel = "threshold=$threshold",
                params = mapOf("topK" to OPTIMAL_TOPK_FROM_B1, "threshold" to threshold),
                results = results,
            )
        }
    }
}
