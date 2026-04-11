package stillframe42.aicodereviewer.rag

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import java.io.File
import java.time.LocalDate


// 검색 품질 평가 결과를 담는 구조화 응답 타입
// Spring AI BeanOutputConverter가 JSON 스키마를 자동 생성하여 Claude 프롬프트에 추가한다
data class QualityEvalResult(
    val score: Int,
    val analysis: String,
    val cause: String?,
)

// 컨벤션 검색 품질 자동 평가 테스트
// 실제 OpenAI(임베딩) + Anthropic(평가) API를 호출하여 검색 품질을 측정하고 결과를 마크다운으로 저장한다.
// AbstractIntegrationTest를 상속하지 않아 WireMock base-url 오버라이드를 받지 않으므로 실제 API가 호출된다.
// API 키는 @DynamicPropertySource에서 application-secret.yml을 직접 파싱하여 주입한다.
// (@DynamicPropertySource가 모든 프로파일 설정보다 최고 우선순위를 가짐)
//
// 실행 방법:
//   ./gradlew qualityEvalTest
@SpringBootTest
@ActiveProfiles("integration-test")
@Tag("quality-eval")
class ConventionSearchQualityEvalTest : Logging {

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
            // application-secret.yml에서 실제 API 키를 직접 파싱하여 주입
            // @DynamicPropertySource는 최고 우선순위로 integration-test 프로파일의 더미 키를 오버라이드한다
            loadSecretApiKeys(registry)
        }

        private val TEST_QUERIES = listOf(
            "Q1" to "Kotlin data class를 Entity로 쓰면 안 되는 이유는?",
            "Q2" to "Spring에서 @Transactional 범위는 어떻게 잡아야 해?",
            "Q3" to "API 응답에 null을 그대로 내려도 되나?",
            "Q4" to "로깅할 때 개인정보는 어떻게 처리해?",
            "Q5" to "N+1 쿼리 문제 해결 방법은?",
            "Q6" to "Kotlin에서 null 안전성을 처리하는 패턴은?",
            "Q7" to "헥사고날 아키텍처에서 의존성 방향 규칙은?",
            "Q8" to "OWASP Top 10에서 Injection 공격을 방어하는 방법은?",
            "Q9" to "코루틴에서 단일 응답과 스트리밍을 어떻게 구분하나?",
            "Q10" to "use-site target을 명시해야 하는 경우는?",
        )

        private const val REPORT_PATH = "plans/202604-1w/search-quality-report.md"

        // application-secret.yml을 ClassPathResource로 로드하여 실제 API 키를 DynamicPropertySource에 등록한다.
        // YamlPropertiesFactoryBean은 중첩 YAML을 openai.api-key 형식으로 플랫화한다.
        // DynamicPropertySource는 최고 우선순위로 integration-test 프로파일의 더미 키를 오버라이드한다.
        private fun loadSecretApiKeys(registry: DynamicPropertyRegistry) {
            val secretResource = ClassPathResource("application-secret.yml")
            if (!secretResource.exists()) return
            val props = YamlPropertiesFactoryBean().apply { setResources(secretResource) }.`object`
                ?: return
            props.getProperty("openai.api-key")?.takeIf { it.startsWith("sk-") }?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
            props.getProperty("anthropic.api-key")?.takeIf { it.startsWith("sk-") }?.let { key ->
                registry.add("anthropic.api-key") { key }
                registry.add("spring.ai.anthropic.api-key") { key }
            }
        }
    }

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    @Qualifier("anthropicChatClient")
    private lateinit var chatClient: ChatClient

    @Test
    fun `검색 품질 평가 결과를 마크다운으로 저장한다`() {
        // 1. 실제 OpenAI 임베딩으로 전체 문서 인덱싱
        runBlocking { conventionIndexUseCase.reindex() }
        val totalChunks = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store",
            Long::class.java,
        ) ?: 0L
        logger.info("인덱싱 완료: {}개 청크", totalChunks)

        // 2. 10개 질문 각각 검색 + Claude 평가
        val queryResults = TEST_QUERIES.map { (label, query) ->
            logger.info("[{}] 검색 중: {}", label, query)
            val docs = vectorPort.search(query, topK = 5, similarityThreshold = 0.0)
            val eval = evaluateWithClaude(query, docs)
            logger.info("[{}] 점수: {}/10 — {}", label, eval.score, eval.analysis)
            QueryResult(label, query, docs, eval)
        }

        // 3. 마크다운 보고서 파일 저장
        File(REPORT_PATH).writeText(buildReport(queryResults, totalChunks))
        logger.info("보고서 저장 완료: {}", REPORT_PATH)
    }

    // 질문과 검색 결과를 Claude에게 전달하여 관련성 점수와 원인을 구조화된 타입으로 반환받는다.
    // BeanOutputConverter가 QualityEvalResult의 JSON 스키마를 프롬프트에 자동으로 추가한다.
    private fun evaluateWithClaude(query: String, docs: List<Document>): QualityEvalResult {
        val docsText = docs.takeIf { it.isNotEmpty() }
            ?.mapIndexed { i, doc ->
                buildString {
                    append("${i + 1}. [${doc.metadata["category"]}] ${doc.metadata["source"]} > \"${doc.metadata["section_header"]}\"")
                    append("\n   ${doc.text?.take(300) ?: ""}")
                }
            }
            ?.joinToString("\n\n")
            ?: "검색 결과 없음"

        return chatClient.prompt()
            .user(
                """
                질문: "$query"

                검색된 컨벤션 문서 (상위 ${docs.size}개):
                $docsText

                위 검색 결과가 질문에 얼마나 관련 있는지 한국어로 평가해주세요:
                - score: 0~10 정수 (10 = 완전히 관련, 0 = 전혀 무관)
                - analysis: 평가 이유 1~2문장
                - cause: score 5 이하일 때만 아래 중 하나, 그 외 null
                  - "문서_내용_부족": 컨벤션 문서에 관련 내용 자체가 없음
                  - "청킹_문제": 내용은 있으나 분할 방식 때문에 검색 안 됨
                  - "임계값_문제": 내용은 있으나 유사도 점수가 낮음
                """.trimIndent()
            )
            .call()
            .entity(QualityEvalResult::class.java)
            ?: QualityEvalResult(score = 0, analysis = "평가 응답 파싱 실패", cause = null)
    }

    private fun buildReport(results: List<QueryResult>, totalChunks: Long): String {
        val avgScore = results.map { it.eval.score }.average()
        val lowScoreResults = results.filter { it.eval.score <= 5 }

        return buildString {
            appendLine("# 검색 품질 평가 보고서")
            appendLine()
            appendLine("- **생성일**: ${LocalDate.now()}")
            appendLine("- **Embedding 모델**: text-embedding-3-small")
            appendLine("- **topK**: 5 / **similarityThreshold**: 0.0 (품질 측정용)")
            appendLine("- **총 인덱싱 청크 수**: $totalChunks")
            appendLine("- **평균 관련성 점수**: ${"%.1f".format(avgScore)} / 10")
            appendLine()
            appendLine("---")
            appendLine()

            results.forEach { (label, query, docs, eval) ->
                appendLine("## [$label] $query")
                appendLine()

                if (docs.isEmpty()) {
                    appendLine("> 검색 결과 없음")
                } else {
                    appendLine("| 순위 | 카테고리 | 파일 | 섹션 |")
                    appendLine("|------|---------|------|------|")
                    docs.forEachIndexed { i, doc ->
                        val category = doc.metadata["category"] ?: "-"
                        val source = doc.metadata["source"] ?: "-"
                        val header = doc.metadata["section_header"] ?: "-"
                        appendLine("| ${i + 1} | $category | $source | $header |")
                    }
                }

                appendLine()
                appendLine("**관련성 평가**: ${eval.score} / 10")
                appendLine()
                appendLine("**평가 근거**: ${eval.analysis}")
                eval.cause?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    appendLine("**원인 분류**: $it")
                }
                appendLine()
                appendLine("---")
                appendLine()
            }

            // Phase 3: 미흡 케이스 분석 (5점 이하)
            if (lowScoreResults.isNotEmpty()) {
                appendLine("## Phase 3: 미흡 케이스 분석 (5점 이하)")
                appendLine()
                appendLine("| 질문 | 점수 | 원인 분류 |")
                appendLine("|------|------|---------|")
                lowScoreResults.forEach { (label, query, _, eval) ->
                    val shortQuery = if (query.length > 25) "${query.take(25)}…" else query
                    appendLine("| [$label] $shortQuery | ${eval.score} | ${eval.cause ?: "—"} |")
                }
                appendLine()

                val causeCounts = lowScoreResults
                    .groupBy { it.eval.cause?.takeIf { c -> c.isNotBlank() } ?: "미분류" }
                    .mapValues { it.value.size }

                appendLine("### 원인별 개선 우선순위")
                appendLine()
                causeCounts.entries.sortedByDescending { it.value }.forEach { (cause, count) ->
                    appendLine("- **$cause**: ${count}건")
                }
                appendLine()
            } else {
                appendLine("## Phase 3: 미흡 케이스 분석")
                appendLine()
                appendLine("5점 이하 케이스 없음 — 모든 질문에서 충분한 관련성 확인.")
                appendLine()
            }
        }
    }

    private data class QueryResult(
        val label: String,
        val query: String,
        val docs: List<Document>,
        val eval: QualityEvalResult,
    )
}
