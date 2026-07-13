package stillframe42.aicodereviewer.rag.integration

import kotlinx.coroutines.runBlocking
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import java.io.File
import java.time.LocalDate

// 3가지 검색 방식(벡터 / 키워드 / 하이브리드) 품질 비교 실험
// LABELED 10개 + EDGE_CASE 5개 쿼리를 top-3 기준으로 gpt-4o-mini가 채점
// 실행 방법: ./gradlew hybridExperimentTest
@Tag("hybrid-experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HybridSearchExperimentTest {

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
            // GitHub API는 이 테스트에서 호출되지 않으므로 더미 주소 사용
            registry.add("github.api.base-url") { "http://localhost:9999" }

            val secretResource = ClassPathResource("application-secret.yml")
            if (!secretResource.exists()) return
            val props = YamlPropertiesFactoryBean().apply { setResources(secretResource) }.`object`
                ?: return
            props.getProperty("openai.api-key")?.takeIf { it.startsWith("sk-") }?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
        }

        private const val REPORT_PATH = "plans/202604-2w/hybrid-search-experiment.md"
    }

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var keywordPort: ConventionKeywordSearchPort

    @Autowired
    private lateinit var hybridService: HybridConventionSearchService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    @field:Qualifier("openAiChatClient")
    private lateinit var chatClient: ChatClient

    @BeforeAll
    fun checkApiKeyAndIndex() {
        val apiKey = environment.getProperty("openai.api-key").orEmpty()
        assumeTrue(apiKey.startsWith("sk-")) {
            "실제 OpenAI API 키(sk-*)가 없습니다. application-secret.yml을 확인하세요."
        }
        runBlocking { conventionIndexUseCase.reindex() }
    }

    private data class ExperimentScores(val vector: Int, val keyword: Int, val hybrid: Int) {
        // 방식 이름으로 점수를 조회한다 (평균 계산 시 사용)
        fun scoreFor(method: String): Int = when (method) {
            "vector" -> vector
            "keyword" -> keyword
            "hybrid" -> hybrid
            else -> -1
        }
    }

    private data class ExperimentRow(
        val label: String,
        val query: String,
        val vectorSummary: String,
        val keywordSummary: String,
        val hybridSummary: String,
        val scores: ExperimentScores,
    ) {
        // 동점 시 hybrid 우선
        val bestMethod: String get() = when {
            scores.hybrid >= scores.vector && scores.hybrid >= scores.keyword -> "hybrid"
            scores.vector >= scores.keyword -> "vector"
            else -> "keyword"
        }

        // 유효 점수(-1 제외)로 우수/열세/중립 판단
        val hybridStatus: String get() {
            if (scores.hybrid < 0) return "중립"
            val best = maxOf(scores.vector, scores.keyword)
            return when {
                scores.vector < 0 && scores.keyword < 0 -> "중립"
                scores.hybrid > best -> "우수"
                scores.hybrid < best -> "열세"
                else -> "중립"
            }
        }
    }

    // top-3 문서를 80자 요약으로 연결한다. 마크다운 표 파이프 이스케이프 포함.
    private fun buildSummary(docs: List<RagDocument>): String =
        if (docs.isEmpty()) "결과 없음"
        else docs.joinToString(" ; ") { doc ->
            doc.text.orEmpty()
                .replace("|", "\\|")
                .replace("\n", " ")
                .take(80)
                .trimEnd() + "..."
        }

    // 쿼리당 gpt-4o-mini 1회 호출로 3가지 방식을 동시 채점한다.
    // 채점 실패 시 -1을 반환하며 실험 자체가 중단되지 않는다.
    private fun autoScore(
        query: String,
        vectorSummary: String,
        keywordSummary: String,
        hybridSummary: String,
    ): ExperimentScores {
        val prompt = """
            질문: $query

            벡터 검색 top-3: $vectorSummary
            키워드 검색 top-3: $keywordSummary
            하이브리드 검색 top-3: $hybridSummary

            각 방식의 관련성을 0~3점으로 채점하라.
            3: 직접 관련 / 2: 부분 관련 / 1: 약간 관련 / 0: 무관
            반드시 JSON만 응답: {"vector": N, "keyword": N, "hybrid": N}
        """.trimIndent()

        return runCatching {
            val content = chatClient.prompt()
                .options(OpenAiChatOptions.builder().model("gpt-4o-mini"))
                .user(prompt)
                .call()
                .content()
                .orEmpty()
            val vector = Regex(""""vector"\s*:\s*(-?\d+)""").find(content)?.groupValues?.get(1)?.toInt() ?: -1
            val keyword = Regex(""""keyword"\s*:\s*(-?\d+)""").find(content)?.groupValues?.get(1)?.toInt() ?: -1
            val hybrid = Regex(""""hybrid"\s*:\s*(-?\d+)""").find(content)?.groupValues?.get(1)?.toInt() ?: -1
            ExperimentScores(vector, keyword, hybrid)
        }.getOrElse { ExperimentScores(-1, -1, -1) }
    }

    // 단일 쿼리에 대해 3가지 검색을 실행하고 채점 결과를 반환한다.
    // ConventionVectorPort.search()는 블로킹 함수이므로 runBlocking 불필요.
    // keywordPort / hybridService는 suspend이므로 runBlocking으로 감싼다.
    private fun runExperiment(label: String, query: String): ExperimentRow {
        val vectorDocs = vectorPort.search(query, topK = 3, similarityThreshold = 0.0)
        val keywordDocs = runBlocking { keywordPort.search(query, topK = 3) }
        val hybridDocs = runBlocking { hybridService.search(query, topK = 3) }
        val vectorSummary = buildSummary(vectorDocs)
        val keywordSummary = buildSummary(keywordDocs)
        val hybridSummary = buildSummary(hybridDocs)
        val scores = autoScore(query, vectorSummary, keywordSummary, hybridSummary)
        return ExperimentRow(label, query, vectorSummary, keywordSummary, hybridSummary, scores)
    }

    // 결과 행 목록을 마크다운 표 형태로 StringBuilder에 추가한다.
    private fun StringBuilder.appendResultTable(rows: List<ExperimentRow>) {
        appendLine("| 질문 | 벡터 top-3 | 키워드 top-3 | 하이브리드 top-3 | 벡터 점수 | 키워드 점수 | 하이브리드 점수 | 최선 방식 |")
        appendLine("|------|-----------|------------|----------------|---------|-----------|--------------|---------|")
        rows.forEach { row ->
            appendLine(
                "| [${row.label}] ${row.query} " +
                "| ${row.vectorSummary} " +
                "| ${row.keywordSummary} " +
                "| ${row.hybridSummary} " +
                "| ${scoreStr(row.scores.vector)} " +
                "| ${scoreStr(row.scores.keyword)} " +
                "| ${scoreStr(row.scores.hybrid)} " +
                "| ${row.bestMethod} |"
            )
        }
        appendLine()
    }

    // 유효하지 않은 점수(-1)는 "?" 로 표기한다.
    private fun scoreStr(score: Int): String = if (score >= 0) score.toString() else "?"

    private fun buildReport(
        labeledResults: List<ExperimentRow>,
        edgeCaseResults: List<ExperimentRow>,
        totalChunks: Long,
    ): String = buildString {
        appendLine("# 하이브리드 검색 품질 비교 실험")
        appendLine()
        appendLine("- **생성일**: ${LocalDate.now()}")
        appendLine("- **총 인덱싱 청크 수**: ${totalChunks}개")
        appendLine()
        appendLine("---")
        appendLine()

        // LABELED 결과 표
        appendLine("## 자연어 쿼리 비교 (LABELED, Q1–Q10)")
        appendLine()
        appendResultTable(labeledResults)

        // EDGE_CASE 결과 표
        appendLine("## 엣지 케이스 쿼리 비교 (EDGE_CASE, E1–E5)")
        appendLine()
        appendLine("> 코드 식별자·약어 형태 — 벡터 검색의 약점을 키워드/하이브리드가 보완하는지 검증")
        appendLine()
        appendResultTable(edgeCaseResults)

        // 방식별 평균 점수
        appendLine("## 방식별 평균 점수")
        appendLine()
        appendLine("| 방식 | LABELED 평균 | EDGE_CASE 평균 | 전체 평균 |")
        appendLine("|------|------------|--------------|---------|")
        listOf("vector", "keyword", "hybrid").forEach { method ->
            val labeledAvg = labeledResults.map { it.scores.scoreFor(method) }.filter { it >= 0 }.average()
            val edgeAvg = edgeCaseResults.map { it.scores.scoreFor(method) }.filter { it >= 0 }.average()
            val totalAvg = (labeledResults + edgeCaseResults).map { it.scores.scoreFor(method) }.filter { it >= 0 }.average()
            val fmt: (Double) -> String = { if (it.isNaN()) "—" else "%.1f".format(it) }
            appendLine("| $method | ${fmt(labeledAvg)} | ${fmt(edgeAvg)} | ${fmt(totalAvg)} |")
        }
        appendLine()

        // 우수/열세 케이스
        val allResults = labeledResults + edgeCaseResults
        val superior = allResults.filter { it.hybridStatus == "우수" }
        val inferior = allResults.filter { it.hybridStatus == "열세" }

        appendLine("## 하이브리드 우수 케이스 (${superior.size}건)")
        appendLine()
        if (superior.isEmpty()) {
            appendLine("없음")
        } else {
            superior.forEach { row ->
                appendLine(
                    "- **[${row.label}]** ${row.query} " +
                    "— 벡터 ${scoreStr(row.scores.vector)} / 키워드 ${scoreStr(row.scores.keyword)} / 하이브리드 ${scoreStr(row.scores.hybrid)}"
                )
            }
        }
        appendLine()

        appendLine("## 하이브리드 열세 케이스 (${inferior.size}건)")
        appendLine()
        if (inferior.isEmpty()) {
            appendLine("없음")
        } else {
            inferior.forEach { row ->
                appendLine(
                    "- **[${row.label}]** ${row.query} " +
                    "— 벡터 ${scoreStr(row.scores.vector)} / 키워드 ${scoreStr(row.scores.keyword)} / 하이브리드 ${scoreStr(row.scores.hybrid)}"
                )
            }
        }
        appendLine()

        // 엣지 케이스 분석
        appendLine("## 엣지 케이스 분석 — 벡터 약점 보완 여부")
        appendLine()
        appendLine("| 레이블 | 질문 | 벡터 점수 | 키워드 점수 | 하이브리드 점수 | 보완 여부 |")
        appendLine("|--------|------|---------|-----------|--------------|---------|")
        edgeCaseResults.forEach { row ->
            val compensated = if (
                (row.scores.keyword >= 0 && row.scores.keyword > row.scores.vector) ||
                (row.scores.hybrid >= 0 && row.scores.hybrid > row.scores.vector)
            ) "✅ 보완됨" else "❌ 미보완"
            appendLine(
                "| ${row.label} | ${row.query} " +
                "| ${scoreStr(row.scores.vector)} " +
                "| ${scoreStr(row.scores.keyword)} " +
                "| ${scoreStr(row.scores.hybrid)} " +
                "| $compensated |"
            )
        }
    }

    @Test
    fun `하이브리드 검색 품질 비교 실험`() {
        val totalChunks = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store",
            Long::class.java,
        ) ?: 0L

        val labeledResults = ConventionTestQueries.LABELED.map { (label, query) ->
            runExperiment(label, query)
        }
        val edgeCaseResults = ConventionTestQueries.EDGE_CASE_QUERIES.map { (label, query) ->
            runExperiment(label, query)
        }

        File(REPORT_PATH).writeText(buildReport(labeledResults, edgeCaseResults, totalChunks))
        println("실험 완료: $REPORT_PATH 저장")
    }
}
