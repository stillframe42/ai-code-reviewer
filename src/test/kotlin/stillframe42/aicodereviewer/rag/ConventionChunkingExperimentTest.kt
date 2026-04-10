package stillframe42.aicodereviewer.rag

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
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
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import java.io.File

// 청킹 전략 실험 테스트 — 실제 OpenAI API와 Testcontainers PostgreSQL을 사용한다.
// 실행 조건:
//   - application-secret.yml에 실제 openai.api-key(sk-*)가 있어야 함
//   - CI에서는 openai.api-key=test-dummy-key이므로 @BeforeAll에서 자동 스킵됨
// 실행 방법: ./gradlew experimentTest
@Tag("experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConventionChunkingExperimentTest {

    companion object {
        // also { it.start() }: 클래스 로드 시점에 즉시 기동 → @DynamicPropertySource 호출 전 준비 완료
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
            // spring.ai.openai.base-url 오버라이드 없음 → 실제 OpenAI API 사용

            // application-integration-test.yml의 더미 키(test-dummy-key)를 실제 키로 복구한다.
            // @DynamicPropertySource는 최고 우선순위이므로 프로파일 오버라이드를 덮어쓸 수 있다.
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

        // 설계 문서 4.4 참조 — 10개 테스트 질문
        val TEST_QUERIES = listOf(
            "Kotlin data class를 Entity로 쓰면 안 되는 이유는?",
            "Spring에서 @Transactional 범위는 어떻게 잡아야 해?",
            "API 응답에 null을 그대로 내려도 되나?",
            "로깅할 때 개인정보는 어떻게 처리해?",
            "N+1 쿼리 문제 해결 방법은?",
            "Kotlin에서 null 안전성을 처리하는 패턴은?",
            "헥사고날 아키텍처에서 의존성 방향 규칙은?",
            "OWASP Top 10에서 Injection 공격을 방어하는 방법은?",
            "코루틴에서 단일 응답과 스트리밍을 어떻게 구분하나?",
            "use-site target을 명시해야 하는 경우는?",
        )
    }

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var environment: Environment

    // OpenAI ChatClient — 자동 채점에 사용 (실험 테스트는 실제 OpenAI API 키를 복구하므로 OpenAI 전용 빈 주입)
    // @field:Qualifier: lateinit var 필드에 Java 어노테이션을 적용할 때 backing field를 명시적으로 타겟팅
    @Autowired
    @field:Qualifier("openAiChatClient")
    private lateinit var chatClient: ChatClient

    // 실제 OpenAI API 키(sk-*)가 없으면 전체 테스트 클래스를 스킵한다.
    // CI: openai.api-key=test-dummy-key → 스킵
    // 로컬: application-secret.yml의 실제 키 → 통과
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
        // 1. 해당 chunkSize로 DocumentPreprocessor 직접 생성 (Spring Bean 우회)
        val splitter = TokenTextSplitter.builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(50)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build()
        val preprocessor = DocumentPreprocessor(splitter)

        // 2. 재인덱싱 (실제 OpenAI /v1/embeddings 호출)
        vectorPort.deleteAll()
        val docs = preprocessor.prepare()
        vectorPort.save(docs)
        println("=== chunkSize=$chunkSize: ${docs.size}개 청크 인덱싱 완료 ===")

        // 3. 10개 질문 검색 + 자동 채점 (실제 OpenAI /v1/embeddings 및 Chat API 호출)
        val results = TEST_QUERIES.map { query ->
            val searchResult = vectorPort.search(query, topK = 3)
            val summary = buildSummary(searchResult)
            val (score, note) = autoScore(query, summary)
            Triple(query, searchResult, score to note)
        }

        // 4. chunking-experiment.md 해당 섹션에 결과 기록
        updateExperimentMarkdown(chunkSize.toString(), docs.size, results)
        println("chunking-experiment.md 업데이트 완료")
    }

    @Test
    fun `MarkdownHeaderSplitter 실험`() {
        // 1. MarkdownHeaderSplitter 직접 생성 (Spring Bean 아님)
        val splitter = MarkdownHeaderSplitter()

        // 2. 재인덱싱 (실제 OpenAI /v1/embeddings 호출)
        vectorPort.deleteAll()
        val docs = splitter.prepare()
        vectorPort.save(docs)
        println("=== MarkdownHeaderSplitter: ${docs.size}개 청크 인덱싱 완료 ===")

        // 3. 10개 질문 검색 + 자동 채점
        val results = TEST_QUERIES.map { query ->
            val searchResult = vectorPort.search(query, topK = 3)
            val summary = buildSummary(searchResult)
            val (score, note) = autoScore(query, summary)
            Triple(query, searchResult, score to note)
        }

        // 4. chunking-experiment.md 실험 2 섹션에 결과 기록
        updateExperimentMarkdown("header", docs.size, results)
        println("chunking-experiment.md 업데이트 완료")
    }

    // 검색 결과 상위 3개 청크를 80자 요약으로 연결한 문자열 생성
    private fun buildSummary(docs: List<Document>): String =
        if (docs.isEmpty()) "결과 없음"
        else docs.joinToString(" ; ") { it.text.orEmpty().take(80).replace("\n", " ").trimEnd() + "..." }

    // OpenAI Chat API로 검색 결과 관련성을 0~3점으로 자동 채점한다.
    // 파싱 실패 시 score=-1, note="채점 실패"를 반환하여 실험을 중단하지 않는다.
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
            val content = chatClient.prompt().user(prompt).call().content().orEmpty()
            val score = Regex(""""score"\s*:\s*(\d)""").find(content)?.groupValues?.get(1)?.toInt() ?: -1
            val note = Regex(""""note"\s*:\s*"([^"]+)"""").find(content)?.groupValues?.get(1) ?: "파싱 실패"
            score to note
        }.getOrElse { -1 to "채점 오류: ${it.message?.take(50)}" }
    }

    // chunking-experiment.md의 해당 섹션을 찾아 총 청크 수, 검색 결과 요약, 점수, 비고를 기록한다.
    private fun updateExperimentMarkdown(
        strategy: String,   // "256" | "512" | "1024" | "header"
        totalChunks: Int,
        results: List<Triple<String, List<Document>, Pair<Int, String>>>,
    ) {
        val section = when (strategy) {
            "256" -> "1-A"
            "512" -> "1-B"
            "1024" -> "1-C"
            "header" -> "2"
            else -> error("알 수 없는 전략: $strategy")
        }
        val projectRoot = File(System.getProperty("user.dir"))
        val file = projectRoot.resolve("plans/202604-1w/chunking-experiment.md")
        require(file.exists()) { "chunking-experiment.md 파일을 찾을 수 없습니다: ${file.absolutePath}" }
        val content = file.readText()

        val sectionStart = content.indexOf("### $section:")
        check(sectionStart >= 0) { "섹션 $section 을 chunking-experiment.md에서 찾을 수 없습니다." }
        val sectionEnd = content.indexOf("\n---", sectionStart).takeIf { it >= 0 } ?: content.length
        var sectionContent = content.substring(sectionStart, sectionEnd)

        // 총 청크 수 업데이트
        sectionContent = sectionContent.replace(
            Regex("- \\*\\*총 청크 수\\*\\*: .*"),
            "- **총 청크 수**: $totalChunks",
        )

        // 각 질문 행 업데이트 (검색 결과 요약 + 점수 + 비고)
        TEST_QUERIES.forEachIndexed { index, _ ->
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
}
