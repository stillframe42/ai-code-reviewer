package stillframe42.aicodereviewer.evaluation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationMetric
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase
import stillframe42.aicodereviewer.evaluation.domain.port.`in`.EvaluationUseCase
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// 골든 데이터셋 20개 케이스 일괄 평가 실행 — 실제 API 호출 (비용 ~$1-2)
// EVAL_MANUAL_TEST=true ./gradlew test --tests "*EvaluationRunnerIT*"
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class EvaluationRunnerIT {

    companion object {
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    private lateinit var evaluationUseCase: EvaluationUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `골든 데이터셋 20개 케이스 일괄 평가 실행`() = runBlocking {
        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader().readText()

        val root = objectMapper.readTree(json)
        val cases: List<GoldenCase> = objectMapper.readValue(root["cases"].toString())

        val results = evaluationUseCase.evaluateAll(cases)

        assertThat(results).hasSize(20)

        printBaselineReport(results)
    }

    private fun printBaselineReport(results: List<EvaluationResult>) {
        println("\n${"=".repeat(80)}")
        println("RAG 평가 베이스라인 결과")
        println("=".repeat(80))

        results.forEach { result ->
            println("\n--- ${result.caseId} ---")
            result.scores.forEach { score ->
                println("  ${score.metric}: ${score.score} — ${score.reason}")
            }
        }

        println("\n${"=".repeat(80)}")
        println("지표별 평균 점수")
        println("=".repeat(80))

        val validResults = results.filter { it.scores.isNotEmpty() }
        EvaluationMetric.entries.forEach { metric ->
            val scores = validResults.mapNotNull { result ->
                result.scores.firstOrNull { it.metric == metric }?.score
            }
            if (scores.isNotEmpty()) {
                val avg = scores.average()
                val min = scores.min()
                val max = scores.max()
                println("  $metric: avg=%.3f, min=%.3f, max=%.3f (n=${scores.size})".format(avg, min, max))
            }
        }

        println("\n${"=".repeat(80)}")
        println("카테고리별 평균 점수")
        println("=".repeat(80))

        listOf("SEC", "ARCH", "STYLE", "API").forEach { prefix ->
            val categoryResults = validResults.filter { it.caseId.startsWith(prefix) }
            if (categoryResults.isNotEmpty()) {
                println("\n  [$prefix]")
                EvaluationMetric.entries.forEach { metric ->
                    val scores = categoryResults.mapNotNull { result ->
                        result.scores.firstOrNull { it.metric == metric }?.score
                    }
                    if (scores.isNotEmpty()) {
                        println("    $metric: %.3f".format(scores.average()))
                    }
                }
            }
        }

        println("\n" + "=".repeat(80))
    }
}
