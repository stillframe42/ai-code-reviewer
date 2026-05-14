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
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationMetric
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase
import stillframe42.aicodereviewer.evaluation.domain.port.`in`.EvaluationUseCase
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import java.io.File

// 골든 데이터셋 20개 케이스 일괄 평가 실행 — 실제 API 호출 (비용 ~$1-2)
// EVAL_MANUAL_TEST=true ./gradlew test --tests "*EvaluationRunnerIT*"
// spring.ai.*.base-url을 오버라이드하지 않아 실제 API 엔드포인트 사용
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class EvaluationRunnerIT {

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
            // STYLE 카테고리 필터 우회 — 측정마다 STEP_A_STYLE_BYPASS env var 로 토글 (default false 보존)
            registry.add("app.rag.style-filter-bypass") { System.getenv("STEP_A_STYLE_BYPASS") ?: "false" }
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

        // application-secret.yml에서 API 키를 읽어 반환
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
            System.err.println("[EvaluationRunnerIT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }
    }

    @Autowired
    private lateinit var evaluationUseCase: EvaluationUseCase

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `골든 데이터셋 20개 케이스 일괄 평가 실행`() = runBlocking {
        // 벡터 스토어에 컨벤션 문서 인덱싱 — 테스트 환경에서는 WireMock으로 인해 자동 인덱싱 실패하므로 수동 실행
        conventionIndexUseCase.reindex()

        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader().readText()

        val root = objectMapper.readTree(json)
        val cases: List<GoldenCase> = objectMapper.readValue(root["cases"].toString())

        // production RagProperties default 와 동일한 값을 명시 전달
        val results = evaluationUseCase.evaluateAll(cases, topK = 3, threshold = 0.7)

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
