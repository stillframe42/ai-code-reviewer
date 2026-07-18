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

// B-3: Multi-query Retrieval 측정 — multiQueryEnabled=true 환경에서 격리 실행
// EvaluationSweepIT과 동일 인프라 패턴 + multi-query-enabled=true 추가
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class EvaluationSweepMultiQueryIT {

    companion object {
        val wireMock = AbstractIntegrationTest.wireMock
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        // sweep 채택값 (topK/threshold)
        const val OPTIMAL_TOPK = 3
        const val OPTIMAL_THRESHOLD = 0.7

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
            // Multi-query 활성화 + STYLE 카테고리 필터 우회
            registry.add("app.rag.style-filter-bypass") { "true" }
            registry.add("app.rag.multi-query-enabled") { "true" }
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
            System.err.println("[EvaluationSweepMultiQueryIT] application-secret.yml 로드 실패: ${e.message}")
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

    @Test
    fun `B-3 Multi-query Evaluation`() {
        conventionIndexUseCase.reindex()
        val cases = loadGoldenCases()
        val results = evaluationUseCase.evaluateAll(
            cases,
            topK = OPTIMAL_TOPK,
            threshold = OPTIMAL_THRESHOLD,
        )
        writeRunResult(
            sweepFile = "multi-query-result.json",
            runLabel = "Multi-query enabled (TopK=$OPTIMAL_TOPK, Threshold=$OPTIMAL_THRESHOLD)",
            params = mapOf(
                "topK" to OPTIMAL_TOPK,
                "threshold" to OPTIMAL_THRESHOLD,
                "multiQueryEnabled" to true,
            ),
            results = results,
        )
    }

    private fun loadGoldenCases(): List<GoldenCase> {
        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader().readText()
        val root = objectMapper.readTree(json)
        return objectMapper.readValue(root["cases"].toString())
    }

    private fun writeRunResult(
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
}
