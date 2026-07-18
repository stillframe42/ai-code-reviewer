package stillframe42.aicodereviewer.evaluation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

// STYLE 5 케이스 전용 escalation sweep.
// 각 전략 단계별 run 라벨(S0 baseline-rerun, S1 code-body-query, ...)로 incremental write.
// 실행: EVAL_MANUAL_TEST=true ./gradlew test --tests "*StyleSearchSweepIT*"
// 결과: plans/202604-3w/sweep-results/style-escalation-sweep.json
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class StyleSearchSweepIT {

    companion object {
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
            // STYLE 신호 확보를 위해 카테고리 필터 우회 고정 (범용 룰 풀이 과도하게 좁아지는 문제 회피)
            registry.add("app.rag.style-filter-bypass") { "true" }

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
            System.err.println("[StyleSearchSweepIT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }
    }

    @Autowired
    private lateinit var evaluationUseCase: EvaluationUseCase

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val sweepFile = "style-escalation-sweep.json"
    private val sweepResultsDir = File("plans/202604-3w/sweep-results").also { it.mkdirs() }

    // STYLE 프리픽스 케이스만 로드 (5개)
    private fun loadStyleCases(): List<GoldenCase> {
        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader().readText()
        val root = objectMapper.readTree(json)
        val allCases: List<GoldenCase> = objectMapper.readValue(root["cases"].toString())
        return allCases.filter { it.id.startsWith("STYLE") }
            .also { check(it.size == 5) { "STYLE 케이스가 5개여야 합니다 (실제: ${it.size})" } }
    }

    // 전략 단계별 run을 incremental append
    private fun writeRunResult(
        runLabel: String,
        params: Map<String, Any>,
        results: List<EvaluationResult>,
    ) {
        val file = File(sweepResultsDir, sweepFile)
        val existing: MutableMap<String, Any> = if (file.exists()) {
            objectMapper.readValue(file)
        } else {
            mutableMapOf("experimentId" to "style-escalation", "runs" to mutableListOf<Any>())
        }
        @Suppress("UNCHECKED_CAST")
        val runs = existing["runs"] as MutableList<Map<String, Any>>

        val recall = results.mapNotNull { r -> r.scores.firstOrNull { it.metric == EvaluationMetric.CONTEXT_RECALL }?.score }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val precision = results.mapNotNull { r -> r.scores.firstOrNull { it.metric == EvaluationMetric.CONTEXT_PRECISION }?.score }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val rpSum = recall + precision
        val pass = recall >= 0.40 || rpSum >= 0.60

        runs += mapOf(
            "label" to runLabel,
            "params" to params,
            "styleRecall" to recall,
            "stylePrecision" to precision,
            "rpSum" to rpSum,
            "passSuccessCriteria" to pass,
            "perCase" to results.map { r ->
                mapOf(
                    "caseId" to r.caseId,
                    "scores" to r.scores.associate { it.metric.name to it.score },
                )
            },
            "executedAt" to Instant.now().toString(),
        )

        file.writeText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(existing))
        println("\n[B1] '$runLabel' 기록 완료 — styleRecall=$recall, stylePrecision=$precision, rpSum=$rpSum, pass=$pass")
    }

    // S0: 베이스라인 재측정 — 현 코드 그대로, STYLE 5 케이스만
    @Test
    fun `S0 baseline rerun`() {
        conventionIndexUseCase.reindex()
        val cases = loadStyleCases()
        val results = evaluationUseCase.evaluateAll(cases, topK = 3, threshold = 0.0)
        writeRunResult(
            runLabel = "S0-baseline-rerun",
            params = mapOf("strategy" to "none", "topK" to 3, "threshold" to 0.0),
            results = results,
        )
    }

    // S1: 전략 1 — 코드 본문 쿼리 (DefaultReviewService 변경 후 측정)
    @Test
    fun `S1 code body query`() {
        conventionIndexUseCase.reindex()
        val cases = loadStyleCases()
        val results = evaluationUseCase.evaluateAll(cases, topK = 3, threshold = 0.0)
        writeRunResult(
            runLabel = "S1-code-body-query",
            params = mapOf("strategy" to "code-body-query", "topK" to 3, "threshold" to 0.0),
            results = results,
        )
    }
}
