package stillframe42.aicodereviewer.agent.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
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
import stillframe42.aicodereviewer.agent.application.AgentReviewService
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

// AgentAnalysisRequest 페이로드 baseline 측정 — 실 OpenAI 임베딩 호출이 필요해 일반 빌드에서는 스킵된다.
// 수동 실행:
//   PAYLOAD_BASELINE=true ./gradlew test --tests "*AgentPayloadBaselineIT*"
// 동일 패턴: RagContextMeasurementIT
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "PAYLOAD_BASELINE", matches = "true")
class AgentPayloadBaselineIT {

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
            registry.add("spring.ai.anthropic.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("langfuse.host") { "http://localhost:${wireMock.port()}" }
            registry.add("agent.python.url") { "http://localhost:${wireMock.port()}" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            // spring.ai.openai.base-url 미설정 — 실 OpenAI API 사용 (mock 임베딩은 HNSW 0건)
            readOpenAiKey()?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
        }

        // application-secret.yml 에서 OpenAI 키 로드 — RagContextMeasurementIT 동일 패턴
        private fun readOpenAiKey(): String? = runCatching {
            val file = File("src/main/resources/application-secret.yml")
            if (!file.exists()) return@runCatching null
            @Suppress("UNCHECKED_CAST")
            val root = Yaml().load<Map<String, Any>>(file.inputStream())
            (root["openai"] as? Map<*, *>)?.get("api-key") as? String
        }.getOrNull()
    }

    @Autowired private lateinit var agentReviewService: AgentReviewService
    @Autowired private lateinit var conventionIndexUseCase: ConventionIndexUseCase
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `AgentAnalysisRequest payload baseline 측정`() = runBlocking {
        Assumptions.assumeTrue(readOpenAiKey() != null) {
            "application-secret.yml 의 openai.api-key 가 필요합니다 (실 임베딩 호출용)"
        }

        seedVectorStore()
        stubPythonAgent()

        var rawSnapshotBody = ""
        val measurements = PayloadSamples.SAMPLES.map { sample ->
            wireMock.resetRequests()
            val patch = PayloadSamples.readPatch(sample)
            val prFiles = PayloadSamples.parsePrFiles(patch)

            agentReviewService.review(
                repositoryFullName = "owner/repo",
                pullRequestNumber = sample.prNumber,
                prDiff = patch,
                prFiles = prFiles,
            )

            val request = wireMock
                .findAll(postRequestedFor(urlPathEqualTo("/agent/analyze")))
                .single()
            val body = request.bodyAsString
            if (sample.id == "security-sql") rawSnapshotBody = body

            decompose(sample.id, body)
        }

        val markdown = PayloadBaselineReport.format(
            measurements = measurements,
            timestamp = LocalDateTime.now().toString(),
            commit = readGitShortSha(),
            rawSnapshotSampleId = "security-sql",
            rawSnapshotBody = rawSnapshotBody,
        )

        val reportFile = File("plans/202605-3w/baseline-payload.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(markdown)

        println("[AgentPayloadBaselineIT] baseline 보고서 생성: ${reportFile.absolutePath}")
        measurements.forEach { println("[AgentPayloadBaselineIT] $it") }

        // 실패해도 보고서 파일은 남아 있어야 사람이 원인을 진단할 수 있다 — 그래서 저장 이후에 검증한다
        check(measurements.all { it.ragContextBytes > 0 }) {
            "ragContext 가 0 인 샘플이 있습니다 — vector_store 시드 또는 OpenAI 호출 실패 가능성"
        }
    }

    private suspend fun seedVectorStore() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store")
        conventionIndexUseCase.reindex()
    }

    // POST /agent/analyze 는 즉시 DONE, GET /agent/analyze/{id} 도 DONE — 폴러가 1회 만에 종료
    private fun stubPythonAgent() {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(okJson("""{"analysis_id":"baseline","status":"DONE","issues":[]}""")),
        )
        wireMock.stubFor(
            get(urlPathMatching("/agent/analyze/.+"))
                .willReturn(okJson("""{"analysis_id":"baseline","status":"DONE","issues":[]}""")),
        )
    }

    private fun decompose(sampleId: String, body: String): Measurement {
        val totalBytes = body.toByteArray(Charsets.UTF_8).size
        val root = objectMapper.readTree(body)
        val diffBytes = root["diff"].asText().toByteArray(Charsets.UTF_8).size
        // 키 이름은 'context_ids' 로 진화했지만 Measurement 의 라벨(ragContextBytes 등)은
        // "RAG 전달에 쓰인 바이트" 의미 유지 — 표현 형식과 무관한 의미론적 안정성
        val ragArray = root["context_ids"]
        val ragContextBytes = ragArray.sumOf { it.asText().toByteArray(Charsets.UTF_8).size }
        val ragJoinedLen = if (ragArray.size() > 0) ragArray[0].asText().length else 0
        return Measurement(
            sampleId = sampleId,
            totalBytes = totalBytes,
            diffBytes = diffBytes,
            ragContextBytes = ragContextBytes,
            metaBytes = totalBytes - diffBytes - ragContextBytes,
            ragChunkCount = ragArray.size(),
            ragJoinedLen = ragJoinedLen,
        )
    }

    private fun readGitShortSha(): String =
        runCatching {
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().readText().trim()
        }.getOrDefault("unknown")
}
