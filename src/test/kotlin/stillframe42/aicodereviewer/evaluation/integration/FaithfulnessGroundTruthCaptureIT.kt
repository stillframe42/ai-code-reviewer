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
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import java.io.File

// Faithfulness GT 데이터 캡처 — Few-shot 5 patch + GT 5 patch 에서 (RAG context + v13 review_text) 추출.
// 결과: plans/202604-3w/sweep-results/faithfulness-capture.json
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class FaithfulnessGroundTruthCaptureIT {

    companion object {
        val wireMock = AbstractIntegrationTest.wireMock
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        // Few-shot 학습 셋 (spec §3.1)
        val FEWSHOT_PATCHES = listOf("SEC-002", "ARCH-001", "STYLE-001", "API-002", "SEC-005")

        // GT 검증 셋 (spec §3.1)
        val GT_PATCHES = listOf("SEC-001", "ARCH-002", "STYLE-002", "API-001", "ARCH-005")

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
            // production 검색 설정 (TopK=3, Threshold=0.7) 그대로 사용 — RagProperties default

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
        }.getOrElse { emptyMap() }
    }

    @Autowired
    private lateinit var hybridSearchService: HybridConventionSearchService

    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `Few-shot 5 + GT 5 patch에 대해 RAG context + v13 review 캡처`() = runBlocking {
        conventionIndexUseCase.reindex()

        val golden = loadGolden()
        val targetIds = (FEWSHOT_PATCHES + GT_PATCHES).toSet()
        val targets = golden.filter { it.id in targetIds }
        check(targets.size == 10) { "10 case가 모두 매칭되어야 합니다 (실제: ${targets.size})" }

        // case별 실패를 격리 — 1건 파싱 실패해도 나머지 9건 진행, 실패 case는 결과에 error로 기록
        val captured = targets.map { case ->
            runCatching {
                val patchContent = javaClass.classLoader
                    .getResourceAsStream("fixtures/evaluation/${case.patchFile}")!!
                    .bufferedReader().readText()
                val fileName = patchContent.lines()
                    .firstOrNull { it.startsWith("diff --git") }
                    ?.substringAfter(" b/")?.trim() ?: "unknown"
                val category = FileCategoryMapper.selectCategory(fileName)

                // production RAG 설정 (TopK=3, Threshold=0.7) 사용
                val docs = hybridSearchService.search(query = fileName, category = category)
                val context = docs.joinToString("\n\n---\n\n") { it.text ?: "" }

                // v13 review (application-ai.yml 활성 프롬프트)
                val codeReview = reviewUseCase.reviewCode(patchContent, AiProvider.ANTHROPIC)
                val reviewText = codeReview.issues.joinToString("\n") { issue ->
                    "[${issue.severity}] ${issue.description}"
                }

                mapOf(
                    "case_id" to case.id,
                    "patch_file" to case.patchFile,
                    "file_name" to fileName,
                    "category" to category.name,
                    "context" to context,
                    "review_text" to reviewText,
                    "set" to (if (case.id in FEWSHOT_PATCHES) "fewshot" else "gt"),
                )
            }.getOrElse { e ->
                println("[Capture WARN] ${case.id} 실패 — ${e.javaClass.simpleName}: ${e.message?.take(200)}")
                mapOf(
                    "case_id" to case.id,
                    "patch_file" to case.patchFile,
                    "set" to (if (case.id in FEWSHOT_PATCHES) "fewshot" else "gt"),
                    "error" to "${e.javaClass.simpleName}: ${e.message?.take(500)}",
                )
            }
        }

        val outFile = File("plans/202604-3w/sweep-results/faithfulness-capture.json")
        outFile.parentFile.mkdirs()
        outFile.writeText(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                mapOf("captured" to captured)
            )
        )
        println("\n[Capture] ${outFile.absolutePath}에 ${captured.size}건 저장 완료")
    }

    private fun loadGolden(): List<GoldenCase> {
        val json = javaClass.classLoader.getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader().readText()
        val root = objectMapper.readTree(json)
        return objectMapper.readValue(root["cases"].toString())
    }
}
