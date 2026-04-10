package stillframe42.aicodereviewer.rag

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.rag.adapter.out.ai.DocumentPreprocessor
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

        // 3. 10개 질문 검색 (실제 OpenAI /v1/embeddings 호출)
        val results = TEST_QUERIES.map { query -> query to vectorPort.search(query, topK = 3) }

        // 4. chunking-experiment.md 해당 섹션에 결과 기록
        updateExperimentMarkdown(chunkSize, docs.size, results)
        println("chunking-experiment.md 업데이트 완료")
    }

    // chunking-experiment.md의 해당 섹션(1-A/1-B/1-C)을 찾아 총 청크 수와 검색 결과 요약을 기록한다.
    // 점수(0~3) 열은 사용자가 직접 채점하므로 공백으로 유지한다.
    private fun updateExperimentMarkdown(
        chunkSize: Int,
        totalChunks: Int,
        results: List<Pair<String, List<Document>>>,
    ) {
        val section = when (chunkSize) {
            256 -> "1-A"
            512 -> "1-B"
            else -> "1-C"
        }
        val projectRoot = File(System.getProperty("user.dir"))
        val file = projectRoot.resolve("plans/202604-1w/chunking-experiment.md")
        require(file.exists()) { "chunking-experiment.md 파일을 찾을 수 없습니다: ${file.absolutePath}" }
        val content = file.readText()

        val sectionStart = content.indexOf("### $section:")
        check(sectionStart >= 0) { "섹션 $section 을 chunking-experiment.md에서 찾을 수 없습니다." }
        val sectionEnd = content.indexOf("\n---", sectionStart).takeIf { it >= 0 } ?: content.length
        var sectionContent = content.substring(sectionStart, sectionEnd)

        // 총 청크 수 업데이트 (기존 값 덮어쓰기)
        sectionContent = sectionContent.replace(
            Regex("- \\*\\*총 청크 수\\*\\*: .*"),
            "- **총 청크 수**: $totalChunks",
        )

        // 각 질문 행 업데이트 (검색된 상위 3개 청크 앞 80자 요약)
        TEST_QUERIES.forEachIndexed { index, _ ->
            val queryNum = "Q${index + 1}"
            val docs = results[index].second
            val summary = if (docs.isEmpty()) "결과 없음"
            else docs.joinToString(" ; ") { it.text.orEmpty().take(80).replace("\n", " ").trimEnd() + "..." }
            sectionContent = sectionContent.replace("| $queryNum | | | |", "| $queryNum | $summary | | |")
        }

        file.writeText(content.substring(0, sectionStart) + sectionContent + content.substring(sectionEnd))
    }
}
