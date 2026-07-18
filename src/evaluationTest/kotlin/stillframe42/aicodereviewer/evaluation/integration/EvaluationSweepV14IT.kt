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

// v14 프롬프트 (v13 + API 예시) 전체 20 case 측정.
// @DynamicPropertySource 로 프롬프트 경로 override — application-ai.yml 변경 없이 테스트.
// 실행: EVAL_MANUAL_TEST=true ./gradlew test --tests "*EvaluationSweepV14IT*"
// 결과: plans/202604-3w/sweep-results/v14-result.json
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class EvaluationSweepV14IT {

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
            // v14 프롬프트 override (production application-ai.yml은 v13 유지)
            registry.add("app.prompt.review-system") { "classpath:prompts/review/review-system-v14.st" }

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
            System.err.println("[EvaluationSweepV14IT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }
    }

    @Autowired
    private lateinit var evaluationUseCase: EvaluationUseCase

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val sweepFile = "v14-result.json"
    private val sweepResultsDir = File("plans/202604-3w/sweep-results").also { it.mkdirs() }

    private fun loadGoldenCases(): List<GoldenCase> {
        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader().readText()
        val root = objectMapper.readTree(json)
        return objectMapper.readValue(root["cases"].toString())
    }

    private fun writeRunResult(
        runLabel: String,
        params: Map<String, Any>,
        results: List<EvaluationResult>,
    ) {
        val file = File(sweepResultsDir, sweepFile)
        val existing: MutableMap<String, Any> = if (file.exists()) {
            objectMapper.readValue(file)
        } else {
            mutableMapOf("experimentId" to "v14-api-fewshot", "runs" to mutableListOf<Any>())
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
        println("\n[B2-1] '$runLabel' 기록 완료 — avg=$avgScores, API=${categoryScores["API"]}")
    }

    @Test
    fun `B2-1 v14 20case sweep`() {
        conventionIndexUseCase.reindex()
        val cases = loadGoldenCases()
        val results = evaluationUseCase.evaluateAll(cases, topK = 3, threshold = 0.7)
        writeRunResult(
            runLabel = "v14-20case",
            params = mapOf("prompt" to "v14", "topK" to 3, "threshold" to 0.7),
            results = results,
        )
    }
}
