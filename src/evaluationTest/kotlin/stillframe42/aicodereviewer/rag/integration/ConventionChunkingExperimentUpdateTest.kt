package stillframe42.aicodereviewer.rag.integration

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.ai.chat.client.ChatClient
import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.transformer.splitter.TokenTextSplitter
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
import stillframe42.aicodereviewer.rag.adapter.out.ai.DocumentPreprocessor
import stillframe42.aicodereviewer.rag.adapter.out.ai.MarkdownHeaderSplitter
import stillframe42.aicodereviewer.rag.adapter.out.ai.OverlappingTokenSplitter
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import java.io.File

// 문서 보강 후 청킹 전략 재실험 — chunking-experiment-update.md에 결과를 기록한다.
// 실험1·2·3 동일한 구성으로 실행하여 문서 보강 전(chunking-experiment.md)과 비교한다.
// 실행 조건:
//   - application-secret.yml에 실제 openai.api-key(sk-*)가 있어야 함
//   - CI에서는 openai.api-key=test-dummy-key이므로 @BeforeAll에서 자동 스킵됨
// 실행 방법: ./gradlew experimentTest
@Tag("experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConventionChunkingExperimentUpdateTest {

    companion object {
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("pgvector/pgvector:pg16").also { it.start() }

        val redis: GenericContainer<*> =
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


        // 원본 실험 점수 — 비교 요약 테이블 업데이트에 사용
        val ORIGINAL_SCORES = mapOf(
            "256" to 1.8,
            "512" to 1.8,
            "1024" to 1.8,
            "header" to 2.0,
            "512-overlap" to 0.8,
        )
    }

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    @field:Qualifier("openAiChatClient")
    private lateinit var chatClient: ChatClient

    // 실제 OpenAI API 키(sk-*)가 없으면 전체 테스트 클래스를 스킵한다.
    @BeforeAll
    fun checkRealApiKey() {
        val apiKey = environment.getProperty("openai.api-key").orEmpty()
        assumeTrue(apiKey.startsWith("sk-")) {
            "실제 OpenAI API 키(sk-*)가 없습니다. application-secret.yml을 확인하세요."
        }
    }

    @ParameterizedTest(name = "chunkSize={0}")
    @ValueSource(ints = [256, 512, 1024])
    fun `TokenTextSplitter chunkSize 실험`(chunkSize: Int) {
        val splitter = TokenTextSplitter.builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(50)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build()
        val preprocessor = DocumentPreprocessor(splitter)

        vectorPort.deleteAll()
        val docs = preprocessor.prepare()
        vectorPort.save(docs)
        println("=== chunkSize=$chunkSize: ${docs.size}개 청크 인덱싱 완료 ===")

        runQueriesAndRecord(chunkSize.toString(), docs.size)
    }

    @Test
    fun `MarkdownHeaderSplitter 실험`() {
        val splitter = MarkdownHeaderSplitter()

        vectorPort.deleteAll()
        val docs = splitter.prepare()
        vectorPort.save(docs)
        println("=== MarkdownHeaderSplitter: ${docs.size}개 청크 인덱싱 완료 ===")

        runQueriesAndRecord("header", docs.size)
    }

    @Test
    fun `TokenTextSplitter(512, overlap=50) 실험`() {
        val splitter = OverlappingTokenSplitter(chunkSize = 512, overlapChars = 200)

        vectorPort.deleteAll()
        val docs = splitter.prepare()
        vectorPort.save(docs)
        println("=== OverlappingTokenSplitter(chunkSize=512, overlapChars=200): ${docs.size}개 청크 인덱싱 완료 ===")

        runQueriesAndRecord("512-overlap", docs.size)
    }

    // 10개 테스트 질문을 검색하고 자동 채점 후 chunking-experiment-update.md에 기록한다.
    private fun runQueriesAndRecord(strategy: String, totalChunks: Int) {
        val results = ConventionTestQueries.QUERIES.map { query ->
            val searchResult = vectorPort.search(query, topK = 3)
            val (score, note) = autoScore(query, buildSummary(searchResult))
            Triple(query, searchResult, score to note)
        }
        updateExperimentMarkdown(strategy, totalChunks, results)

        // 모든 전략 실험이 완료되면 비교 요약 테이블을 업데이트한다.
        // 단순히 매 전략 실행 후 갱신하여 마지막 실행 시 최종 상태가 된다.
        updateComparisonSummary(strategy, results)

        println("chunking-experiment-update.md 업데이트 완료 (strategy=$strategy)")
    }

    // 검색 결과 상위 3개 청크를 80자 요약으로 연결한 문자열 생성
    private fun buildSummary(docs: List<RagDocument>): String =
        if (docs.isEmpty()) "결과 없음"
        else docs.joinToString(" ; ") {
            it.text.orEmpty()
                .replace("|", "\\|")
                .take(80)
                .replace("\n", " ")
                .trimEnd() + "..."
        }

    // OpenAI Chat API로 검색 결과 관련성을 0~3점으로 자동 채점한다.
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
                .options(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
                .user(prompt)
                .call()
                .content()
                .orEmpty()
            val score = Regex(""""score"\s*:\s*(-?\d+)""").find(content)?.groupValues?.get(1)?.toInt() ?: -1
            val note = Regex(""""note"\s*:\s*"([^"]+)"""").find(content)?.groupValues?.get(1) ?: "파싱 실패"
            score to note
        }.getOrElse { -1 to "채점 오류: ${it.message?.take(50)}" }
    }

    // chunking-experiment-update.md의 해당 섹션을 찾아 결과를 기록한다.
    private fun updateExperimentMarkdown(
        strategy: String,
        totalChunks: Int,
        results: List<Triple<String, List<RagDocument>, Pair<Int, String>>>,
    ) {
        val section = when (strategy) {
            "256" -> "1-A"
            "512" -> "1-B"
            "1024" -> "1-C"
            "header" -> "2"
            "512-overlap" -> "3"
            else -> error("알 수 없는 전략: $strategy")
        }
        val projectRoot = File(System.getProperty("user.dir"))
        val file = projectRoot.resolve("plans/202604-1w/chunking-experiment-update.md")
        require(file.exists()) { "chunking-experiment-update.md 파일을 찾을 수 없습니다: ${file.absolutePath}" }
        val content = file.readText()

        val sectionStart = content.indexOf("### $section:")
        check(sectionStart >= 0) { "섹션 $section 을 chunking-experiment-update.md에서 찾을 수 없습니다." }
        val sectionEnd = content.indexOf("\n---", sectionStart).takeIf { it >= 0 } ?: content.length
        var sectionContent = content.substring(sectionStart, sectionEnd)

        // 총 청크 수 업데이트
        sectionContent = sectionContent.replace(
            Regex("- \\*\\*총 청크 수\\*\\*: .*"),
            "- **총 청크 수**: $totalChunks",
        )

        // 각 질문 행 업데이트
        ConventionTestQueries.QUERIES.forEachIndexed { index, _ ->
            val queryNum = "Q${index + 1}"
            val (_, docs, scoreNote) = results[index]
            val (score, note) = scoreNote
            val summary = buildSummary(docs)
            val scoreStr = if (score >= 0) score.toString() else "?"
            sectionContent = sectionContent.replace(
                "| $queryNum | | | |",
                "| $queryNum | $summary | $scoreStr | $note |",
            )
        }

        // 평균 점수 계산 및 업데이트
        val validScores = results.map { it.third.first }.filter { it >= 0 }
        if (validScores.isNotEmpty()) {
            val avg = validScores.average()
            val avgStr = "**%.1f**".format(avg)
            sectionContent = sectionContent.replace(
                "| **평균** | | | |",
                "| **평균** | | $avgStr | |",
            )
        }

        file.writeText(content.substring(0, sectionStart) + sectionContent + content.substring(sectionEnd))
    }

    // 비교 요약 테이블의 "보강 후 평균" 열을 실험 결과로 업데이트한다.
    private fun updateComparisonSummary(
        strategy: String,
        results: List<Triple<String, List<RagDocument>, Pair<Int, String>>>,
    ) {
        val strategyLabel = when (strategy) {
            "256" -> "TokenTextSplitter(256)"
            "512" -> "TokenTextSplitter(512)"
            "1024" -> "TokenTextSplitter(1024)"
            "header" -> "MarkdownHeaderSplitter"
            "512-overlap" -> "TokenTextSplitter(512, overlap=50)"
            else -> return
        }
        val validScores = results.map { it.third.first }.filter { it >= 0 }
        if (validScores.isEmpty()) return
        val avg = validScores.average()
        val originalAvg = ORIGINAL_SCORES[strategy] ?: return
        val change = when {
            avg > originalAvg -> "+%.1f".format(avg - originalAvg)
            avg < originalAvg -> "%.1f".format(avg - originalAvg)
            else -> "변화 없음"
        }

        val projectRoot = File(System.getProperty("user.dir"))
        val file = projectRoot.resolve("plans/202604-1w/chunking-experiment-update.md")
        val content = file.readText()

        // "| 전략명 | 보강 전 | — | — |" 패턴을 실제 값으로 치환
        val originalAvgStr = "%.1f".format(originalAvg)
        val newAvgStr = "%.1f".format(avg)
        val updated = content.replace(
            "| $strategyLabel | $originalAvgStr | — | — |",
            "| $strategyLabel | $originalAvgStr | $newAvgStr | $change |",
        )
        file.writeText(updated)
    }
}
