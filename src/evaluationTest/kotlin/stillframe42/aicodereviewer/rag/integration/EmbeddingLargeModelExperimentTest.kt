package stillframe42.aicodereviewer.rag.integration

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.ai.chat.client.ChatClient
import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import stillframe42.aicodereviewer.rag.domain.port.out.DocumentPreparerPort
import java.io.File

// text-embedding-3-large 모델 검색 품질 실험
// DynamicPropertySource에서 migration-large 경로 추가 → V9 마이그레이션으로 3072차원 테이블 사용
// 실행 방법: ./gradlew experimentTest --tests "*EmbeddingLargeModelExperimentTest"
@Tag("experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbeddingLargeModelExperimentTest {

    companion object {
        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer("pgvector/pgvector:pg16").also { it.start() }

        private val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }

            // 3072차원 마이그레이션 포함 — V9가 embedding 컬럼을 3072로 변경
            registry.add("spring.flyway.locations") {
                "classpath:db/migration,classpath:db/migration-large"
            }
            registry.add("spring.ai.openai.embedding.options.model") { "text-embedding-3-large" }
            registry.add("spring.ai.openai.embedding.options.dimensions") { "3072" }
            registry.add("spring.ai.vectorstore.pgvector.dimensions") { "3072" }

            val secretResource = ClassPathResource("application-secret.yml")
            if (secretResource.exists()) {
                val props = YamlPropertiesFactoryBean().apply { setResources(secretResource) }.`object`
                val realKey = props?.getProperty("openai.api-key").orEmpty()
                if (realKey.startsWith("sk-")) {
                    registry.add("openai.api-key") { realKey }
                    registry.add("spring.ai.openai.api-key") { realKey }
                }
            }
        }

    }

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var splitter: DocumentPreparerPort

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    @field:Qualifier("openAiChatClient")
    private lateinit var chatClient: ChatClient

    @BeforeAll
    fun checkRealApiKey() {
        val apiKey = environment.getProperty("openai.api-key").orEmpty()
        assumeTrue(apiKey.startsWith("sk-")) {
            "실제 OpenAI API 키(sk-*)가 없습니다. application-secret.yml을 확인하세요."
        }
    }

    @Test
    fun `text-embedding-3-large 검색 품질 실험`() {
        vectorPort.deleteAll()
        val docs = splitter.prepare()
        vectorPort.save(docs)
        println("=== text-embedding-3-large: ${docs.size}개 청크 인덱싱 완료 ===")
        runQueriesAndRecord("large")
    }

    private data class QueryResult(val summary: String, val score: Int, val note: String, val elapsedMs: Long)

    private fun runQueriesAndRecord(model: String) {
        val results = ConventionTestQueries.QUERIES.map { query ->
            val start = System.currentTimeMillis()
            val docs = vectorPort.search(query, topK = 3)
            val elapsed = System.currentTimeMillis() - start
            val summary = buildSummary(docs)
            val (score, note) = autoScore(query, summary)
            QueryResult(summary, score, note, elapsed)
        }
        updateExperimentMarkdown(model, results)
        println("embedding-experiment.md 업데이트 완료 (model=$model)")
    }

    private fun buildSummary(docs: List<RagDocument>): String =
        if (docs.isEmpty()) "결과 없음"
        else docs.joinToString(" ; ") {
            it.text.orEmpty()
                .replace("|", "\\|")
                .take(80)
                .replace("\n", " ")
                .trimEnd() + "..."
        }

    private fun autoScore(query: String, summary: String): Pair<Int, String> {
        val prompt = """
            질문: $query
            검색된 상위 청크 요약: $summary

            아래 기준으로 관련성을 0~3점으로 채점하고, 한 문장 한국어 비고를 작성하라.
            반드시 JSON만 응답하라: {"score": N, "note": "..."}

            채점 기준:
            3: 질문과 직접 관련된 내용이 검색 상위 결과에 포함됨
            2: 관련 내용이 검색되었으나 핵심 설명이 일부 누락됨
            1: 약간 관련 있는 내용이 검색되었으나 답변에 활용하기 어려움
            0: 무관한 내용이 검색되거나 관련 결과 없음
        """.trimIndent()

        return runCatching {
            val content = chatClient.prompt()
                .options(OpenAiChatOptions.builder().model("gpt-4o-mini"))
                .user(prompt)
                .call()
                .content()
                .orEmpty()
            val score = Regex(""""score"\s*:\s*(-?\d+)""").find(content)?.groupValues?.get(1)?.toInt() ?: -1
            val note = Regex(""""note"\s*:\s*"([^"]+)"""").find(content)?.groupValues?.get(1) ?: "파싱 실패"
            score to note
        }.getOrElse { -1 to "채점 오류: ${it.message?.take(50)}" }
    }

    private fun updateExperimentMarkdown(model: String, results: List<QueryResult>) {
        val projectRoot = File(System.getProperty("user.dir"))
        val file = projectRoot.resolve("plans/202604-1w/embedding-experiment.md")
        require(file.exists()) { "embedding-experiment.md 파일을 찾을 수 없습니다: ${file.absolutePath}" }
        var content = file.readText()

        // 각 질문 행 업데이트
        results.forEachIndexed { index, result ->
            val queryNum = "Q${index + 1}"
            val scoreStr = if (result.score >= 0) result.score.toString() else "?"
            content = content.replace(
                "| $queryNum | | | |",
                "| $queryNum | ${result.summary} | $scoreStr | ${result.note} |",
            )
        }

        // 평균 점수 업데이트
        val validScores = results.map { it.score }.filter { it >= 0 }
        if (validScores.isNotEmpty()) {
            val avg = validScores.average()
            content = content.replace(
                "| **평균** | | | |",
                "| **평균** | | **${"%.1f".format(avg)}** | |",
            )
        }

        // 평균 응답 시간 업데이트
        val avgElapsed = results.map { it.elapsedMs }.average().toLong()
        content = content.replace(
            "- **평균 응답 시간**: — ms",
            "- **평균 응답 시간**: $avgElapsed ms",
        )

        // 최종 비교 요약 업데이트
        val avgScore = if (validScores.isNotEmpty()) "%.1f".format(validScores.average()) else "—"
        val (modelLabel, dims) = if (model == "small") "text-embedding-3-small" to "1536" else "text-embedding-3-large" to "3072"
        content = content.replace(
            "| $modelLabel | $dims | — | — |",
            "| $modelLabel | $dims | $avgScore | $avgElapsed |",
        )

        file.writeText(content)
    }
}
