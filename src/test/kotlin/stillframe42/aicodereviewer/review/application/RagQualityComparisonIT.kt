package stillframe42.aicodereviewer.review.application

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.rag.application.ConventionContextService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// RAG 적용 전/후 리뷰 품질 비교용 수동 실행 테스트
// AbstractIntegrationTest를 상속하지 않음 — Anthropic/OpenAI API를 WireMock으로 리다이렉트하지 않기 위해
// (실제 임베딩 없이는 HNSW 벡터 검색이 degenerate 그래프로 결과를 반환하지 않음)
//
// 실행 방법: RAG_MANUAL_TEST=true 환경 변수 설정 후 실행
//   RAG_MANUAL_TEST=true ./gradlew test --tests "*RagQualityComparisonIT*"
// 일반 빌드(./gradlew test)에서는 자동 스킵되어 실제 API 호출 없음
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "RAG_MANUAL_TEST", matches = "true")
class RagQualityComparisonIT {

    companion object {
        // AbstractIntegrationTest의 Singleton 컨테이너 재사용 — 새 컨테이너 기동 없이 기존 인스턴스 공유
        val wireMock = AbstractIntegrationTest.wireMock
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        // 아키텍처 컨벤션 관련 키워드 — regex 기반 정확 매칭.
        // case-sensitive + 앞뒤에 ASCII 알파벳이 오지 않을 때만 매칭한다.
        // - 영문 키워드: import→port, default→Default 같은 substring 오매칭을 차단
        // - 한글 키워드: 앞뒤에 영문자가 올 수 없으므로 항상 안전하게 매칭됨
        //   (\b 단어 경계는 Kotlin Regex가 ASCII만 인식하므로 한글에 무용)
        internal val keywordPatterns: List<Pair<String, Regex>> = listOf(
            "UseCase", "Default", "포트", "port", "헥사고날", "hexagonal", "컨벤션",
        ).map { kw -> kw to Regex("(?<![A-Za-z])${Regex.escape(kw)}(?![A-Za-z])") }

        // 키워드별 출현 횟수를 Map으로 반환한다. 키 순서는 keywordPatterns 순서를 따른다.
        internal fun countByKeyword(text: String): Map<String, Int> =
            keywordPatterns.associate { (kw, regex) -> kw to regex.findAll(text).count() }

        // 모든 키워드 출현 횟수의 총합을 반환한다.
        internal fun countKeywords(text: String): Int =
            countByKeyword(text).values.sum()

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
            // spring.ai.anthropic.base-url, spring.ai.openai.base-url 미설정
            // → application-ai.yml 기본값(실제 API) 사용
            // 이유: WireMock mock 임베딩([0.1...0.1])은 모든 벡터가 동일하여
            //       HNSW 인덱스가 degenerate 그래프가 되고 유사도 검색이 0건을 반환한다.
            //       실제 품질 비교를 위해 실제 OpenAI 임베딩이 필요하다.
            val secrets = readSecrets()
            secrets["anthropic"]?.let { key ->
                registry.add("anthropic.api-key") { key }
                // placeholder ${anthropic.api-key} 해석 시점 문제를 피해 직접 등록
                registry.add("spring.ai.anthropic.api-key") { key }
            }
            secrets["openai"]?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
        }

        // application-secret.yml에서 API 키 맵을 읽어 반환한다.
        // 클래스패스 로드가 불안정하므로 파일시스템에서 직접 읽는다 (Gradle 실행 시 워킹 디렉토리 = 프로젝트 루트)
        // 반환 맵 키: "anthropic", "openai" (값이 없거나 읽기 실패 시 해당 키 부재)
        private fun readSecrets(): Map<String, String> = runCatching {
            val file = java.io.File("src/main/resources/application-secret.yml")
            check(file.exists()) { "application-secret.yml 파일을 찾을 수 없습니다: ${file.absolutePath}" }
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(file.inputStream())
            buildMap {
                (map["anthropic"] as? Map<*, *>)?.get("api-key")?.let { put("anthropic", it as String) }
                (map["openai"] as? Map<*, *>)?.get("api-key")?.let { put("openai", it as String) }
            }
        }.getOrElse { e ->
            System.err.println("[RagQualityComparisonIT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }

        // PR #11 diff: stillframe42/code-reviewer-tester
        // 의도적 컨벤션 위반:
        //   1. OrderController가 OrderUseCase 인터페이스 대신 OrderService 구현체에 직접 의존
        //   2. 클래스명 OrderService (헥사고날 컨벤션상 DefaultOrderService 여야 함)
        //   3. OrderUseCase 포트 인터페이스 부재
        private val DIFF: String = checkNotNull(
            RagQualityComparisonIT::class.java.getResourceAsStream("/fixtures/review/pr11-order-diff.patch")
        ) { "fixtures/review/pr11-order-diff.patch 를 찾을 수 없습니다" }
            .bufferedReader()
            .readText()
    }

    @Autowired
    private lateinit var aiReviewPort: AiReviewPort

    @Autowired
    private lateinit var conventionContextService: ConventionContextService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    @BeforeEach
    fun setUp() {
        // 테스트 간 stub 오염 방지
        wireMock.resetAll()
        // OpenAI 임베딩은 실제 API 사용 — WireMock stub 불필요
        // Langfuse 스텁 (리뷰 완료 후 관측 데이터 전송 시 필요)
        WireMockStubs.stubLangfuseIngestion(wireMock)
        // Redis 캐시 초기화 — 이전 리뷰 캐시 제거
        redisTemplate.connectionFactory
            .reactiveConnection
            .serverCommands()
            .flushAll()
            .block()
    }

    @Test
    fun `RAG 전후 리뷰 품질 비교`(): Unit = runBlocking {
        val secrets = readSecrets()
        // 실제 API 키가 없으면 건너뜀 — 401 에러 대신 명확한 skip 메시지 제공
        Assumptions.assumeTrue(secrets.containsKey("anthropic") && secrets.containsKey("openai")) {
            "application-secret.yml에서 Anthropic/OpenAI API 키를 읽지 못했습니다. 실제 API 키가 필요한 수동 실행 테스트입니다."
        }

        // OrderController.kt는 API 카테고리로 분류되어 api-design.md만 검색된다.
        // 의도한 컨벤션 위반(UseCase 없이 구현체 직접 의존, DefaultXxx 명명 미준수)은
        // ARCH 카테고리(architecture-guide.md)에 정의되어 있으므로
        // OrderService.kt 경로를 기준으로 ARCH 컨벤션을 가져온다.
        val filePath = "src/main/kotlin/stillframe42/codereviewertester/order/application/OrderService.kt"
        val query = "OrderService.kt"

        // Before: vector_store 비운 상태 → buildContext가 빈 문자열 반환 → conventionContext = null
        jdbcTemplate.execute("DELETE FROM vector_store")
        val contextBefore = conventionContextService.buildContext(query = query, filePath = filePath)
        val reviewBefore = aiReviewPort.reviewCode(
            code = DIFF,
            provider = AiProvider.ANTHROPIC,
            conventionContext = null,
        )
        writeResult(
            filename = "plans/202604-2w/rag-quality-before.md",
            label = "전",
            context = contextBefore,
            review = reviewBefore,
        )

        // After: reindex 후 buildContext → 아키텍처 컨벤션 포함 → conventionContext 주입
        conventionIndexUseCase.reindex()
        val contextAfter = conventionContextService.buildContext(query = query, filePath = filePath)
        val reviewAfter = aiReviewPort.reviewCode(
            code = DIFF,
            provider = AiProvider.ANTHROPIC,
            conventionContext = contextAfter.ifBlank { null },
        )
        writeResult(
            filename = "plans/202604-2w/rag-quality-after.md",
            label = "후",
            context = contextAfter,
            review = reviewAfter,
        )

        // 1차 검증: RAG 파이프라인이 convention context를 실제로 검색했는가
        // AI 출력은 비결정적이므로 특정 키워드 보유 여부가 아닌, 컨텍스트 주입 여부를 먼저 검증한다.
        assertThat(contextAfter)
            .withFailMessage("reindex 후 ARCH 컨벤션 컨텍스트가 검색되어야 합니다. vector_store 또는 RRF 검색을 확인하세요.")
            .isNotBlank()

        // 2차 검증: 리뷰 출력에 컨벤션 키워드가 실제로 반영되었는가 (상대 비교)
        val beforeCount = countKeywords(reviewBefore.toFullText())
        val afterCount = countKeywords(reviewAfter.toFullText())
        println("[RAG 품질 비교] 컨벤션 키워드 출현 횟수: before=$beforeCount, after=$afterCount")
        println("[RAG 품질 비교] 검색된 컨텍스트 길이: ${contextAfter.length}자")

        // after는 최소 1회 이상 컨벤션 키워드를 언급해야 한다
        assertThat(afterCount)
            .withFailMessage(
                "RAG 적용 후 리뷰에 컨벤션 키워드가 최소 1회 이상 포함되어야 합니다 " +
                    "(before=$beforeCount, after=$afterCount)",
            )
            .isGreaterThanOrEqualTo(1)

        // after는 before보다 엄격히 많아야 한다 (주입 효과가 실제로 나타났는가)
        assertThat(afterCount)
            .withFailMessage(
                "RAG 적용 후 리뷰의 컨벤션 키워드 수가 before보다 많아야 합니다 " +
                    "(before=$beforeCount, after=$afterCount)",
            )
            .isGreaterThan(beforeCount)
    }

    // 리뷰 결과를 지정 파일에 마크다운 형식으로 저장한다
    private fun writeResult(filename: String, label: String, context: String, review: CodeReview) {
        val now = LocalDateTime.now()
        // 부모 디렉토리가 없으면 자동 생성 (plans/202604-2w/ 등)
        File(filename).parentFile?.mkdirs()
        val fullText = review.toFullText()
        val countDetails = countByKeyword(fullText).entries.joinToString("\n") { (kw, count) ->
            "- \"$kw\": ${count}회"
        }
        File(filename).writeText(buildString {
            appendLine("# RAG 적용 ${label} 리뷰 결과")
            appendLine()
            appendLine("## 테스트 조건")
            appendLine("- PR: stillframe42/code-reviewer-tester#11")
            appendLine("- 파일: OrderController.kt, OrderService.kt")
            appendLine("- 실행일시: $now")
            appendLine()
            appendLine("## 주입된 컨벤션 컨텍스트")
            appendLine(if (context.isBlank()) "없음" else context)
            appendLine()
            appendLine("## AI 리뷰 결과")
            appendLine("**총점:** ${review.overallScore}/10")
            appendLine()
            appendLine("**총평:**")
            appendLine(review.summary)
            appendLine()
            appendLine("**이슈 목록:**")
            review.issues.forEach { issue ->
                appendLine("- [${issue.severity}] ${issue.description}")
                appendLine("  - 제안: ${issue.suggestion}")
            }
            appendLine()
            appendLine("## 컨벤션 관련 피드백 언급 횟수")
            appendLine(countDetails)
            appendLine("- 합계: ${countKeywords(fullText)}회")
        })
    }

    // 리뷰 결과 텍스트(summary + 각 이슈의 description/suggestion)를 하나의 문자열로 조립한다.
    // 키워드 카운팅과 리포트 생성이 같은 소스를 바라보도록 단일 지점으로 통일한다.
    private fun CodeReview.toFullText(): String =
        summary + "\n" + issues.joinToString("\n") { "${it.description} ${it.suggestion}" }

}
