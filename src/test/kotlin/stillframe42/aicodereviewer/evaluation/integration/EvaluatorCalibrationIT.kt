package stillframe42.aicodereviewer.evaluation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.document.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.evaluation.domain.port.out.RagEvaluationPort
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import java.io.File
import java.time.Instant

// Sub-plan A: GT 10 case로 evaluator 단계별 측정.
// 단계는 환경변수로 결정:
//  - 단계 1 (Few-shot mini): EVAL_FAITHFULNESS_FEWSHOT=true
//  - 단계 2 (gpt-4o):       APP_RAG_EVALUATION_MODEL=gpt-4o
//  - 단계 3 (둘 다):         두 환경변수 모두 set
//  - baseline (단계 표시):  둘 다 unset
//
// 결과는 plans/202604-3w/sweep-results/evaluator-calibration-<stage>.json에 저장.
// success criteria: GOOD avg ≥ 0.7, HALL avg ≤ 0.3, gap ≥ 0.4.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class EvaluatorCalibrationIT {

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
            // 환경변수 → property 명시적 매핑
            registry.add("app.rag.evaluation.faithfulness-fewshot") {
                System.getenv("EVAL_FAITHFULNESS_FEWSHOT") ?: "false"
            }
            registry.add("app.rag.evaluation.model") {
                System.getenv("APP_RAG_EVALUATION_MODEL") ?: "gpt-4o-mini"
            }

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
            check(file.exists())
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(file.inputStream())
            buildMap {
                (map["openai"] as? Map<*, *>)?.get("api-key")?.let { put("openai", it as String) }
                (map["anthropic"] as? Map<*, *>)?.get("api-key")?.let { put("anthropic", it as String) }
            }
        }.getOrElse { emptyMap() }
    }

    @Autowired
    private lateinit var ragEvaluationPort: RagEvaluationPort

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `GT 10 case로 단계별 evaluator 측정`(): Unit = runBlocking {
        val gtJson = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/faithfulness-ground-truth.json")!!
            .bufferedReader().readText()
        @Suppress("UNCHECKED_CAST")
        val gt: Map<String, List<Map<String, Any>>> = objectMapper.readValue(
            gtJson,
            objectMapper.typeFactory.constructMapType(
                Map::class.java,
                String::class.java,
                List::class.java,
            ),
        ) as Map<String, List<Map<String, Any>>>

        val goodResults = (gt["good"] ?: emptyList()).map { case -> evaluateOne(case) }
        val hallResults = (gt["hallucination"] ?: emptyList()).map { case -> evaluateOne(case) }

        val goodAvg = goodResults.map { it["score"] as Double }.average()
        val hallAvg = hallResults.map { it["score"] as Double }.average()
        val gap = goodAvg - hallAvg
        val passed = goodAvg >= 0.7 && hallAvg <= 0.3 && gap >= 0.4

        val stage = inferStage()
        val outFile = File("plans/202604-3w/sweep-results/evaluator-calibration-${stage}.json")
        outFile.parentFile.mkdirs()
        val report = mapOf(
            "stage" to stage,
            "fewshotEnabled" to (System.getenv("EVAL_FAITHFULNESS_FEWSHOT") ?: "false"),
            "model" to (System.getenv("APP_RAG_EVALUATION_MODEL") ?: "gpt-4o-mini"),
            "summary" to mapOf(
                "goodAvg" to goodAvg,
                "hallAvg" to hallAvg,
                "gap" to gap,
                "passed" to passed,
                "criteria" to "goodAvg≥0.7, hallAvg≤0.3, gap≥0.4",
            ),
            "good" to goodResults,
            "hallucination" to hallResults,
            "executedAt" to Instant.now().toString(),
        )
        outFile.writeText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report))

        println("\n${"=".repeat(80)}")
        println("[Calibration $stage] goodAvg=${"%.3f".format(goodAvg)}, hallAvg=${"%.3f".format(hallAvg)}, gap=${"%.3f".format(gap)}, PASSED=$passed")
        println("결과: ${outFile.absolutePath}")
        println("=".repeat(80))

        // sanity check (실패 시 다음 stage로 escalation 결정)
        assertThat(goodResults.size).isEqualTo(5)
        assertThat(hallResults.size).isEqualTo(5)
    }

    private suspend fun evaluateOne(case: Map<String, Any>): Map<String, Any> {
        val context = case["context"] as String
        val reviewText = case["review_text"] as String
        // Document에 context를 단일 chunk로 wrap (Faithfulness 평가 시 join하므로 1개여도 OK)
        val ctxDocs = listOf(Document.builder().text(context).build())
        val score = ragEvaluationPort.evaluateFaithfulness(ctxDocs, reviewText)
        return mapOf(
            "case_id" to (case["case_id"] as String),
            "score" to score.score,
            "reason" to score.reason,
            "expected_score_min" to (case["expected_score_min"] as Number).toDouble(),
            "expected_score_max" to (case["expected_score_max"] as Number).toDouble(),
        )
    }

    // 환경변수로 단계 식별 (파일명용)
    private fun inferStage(): String {
        val fewshot = (System.getenv("EVAL_FAITHFULNESS_FEWSHOT") ?: "false").toBoolean()
        val model = System.getenv("APP_RAG_EVALUATION_MODEL") ?: "gpt-4o-mini"
        return when {
            fewshot && model == "gpt-4o-mini" -> "stage1"
            !fewshot && model == "gpt-4o" -> "stage2"
            fewshot && model == "gpt-4o" -> "stage3"
            else -> "baseline"
        }
    }
}
