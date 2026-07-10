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
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionContextUseCase
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

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
            // 방향 D: integration-test profile이 app.ai.reviewer.*-model을
            // test-haiku-model / test-sonnet-model로 override(WireMock stub용 가짜 이름)하는데,
            // 본 테스트는 실제 Anthropic API를 호출하므로 404가 발생한다.
            // 프로덕션 application-ai.yml 의 기본값으로 재-override하여 실제 모델 호출이 가능하게 한다.
            registry.add("app.ai.reviewer.default-model") { "claude-haiku-4-5-20251001" }
            registry.add("app.ai.reviewer.critical-model") { "claude-sonnet-4-6" }
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

        // fixture basename으로 src/test/resources/fixtures/review/ 하위 파일을 로드한다.
        // .patch 를 먼저 찾고 없으면 .kt 를 찾는다. 둘 다 없으면 예외.
        // PR 전체 diff(.patch)와 단일 파일 raw 소스(.kt) 양쪽을 같은 인터페이스로 지원한다.
        internal fun loadFixture(name: String): String {
            val candidates = listOf("$name.patch", "$name.kt")
            for (candidate in candidates) {
                val stream = RagQualityComparisonIT::class.java
                    .getResourceAsStream("/fixtures/review/$candidate")
                if (stream != null) return stream.bufferedReader().readText()
            }
            error("fixture 파일을 찾을 수 없습니다: $name (.patch 또는 .kt 필요)")
        }
    }

    // 방향 D: 프로덕션 경로(DefaultReviewService → DiffPreprocessor → reviewParallel →
    // 파일별 buildContext → aggregate)를 그대로 거치는 테스트. AiReviewPort 직접 호출은
    // 프로덕션과 다른 single-shot 경로였기 때문에 제거함.
    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    // 진단 목적 — reviewAfter 완료 후 같은 filePath로 buildContext를 read-only 재호출하여
    // 파일별 retrieval 길이를 샘플링하는 용도. 실제 assertion은 리뷰 출력에 기반한다.
    @Autowired
    private lateinit var conventionContextService: ConventionContextUseCase

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

        val repeatN = (System.getenv("RAG_REPEAT_N")?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val fixtureName = System.getenv("RAG_FIXTURE") ?: "pr11-order"
        val diff = loadFixture(fixtureName)
        println("[RAG] fixture=$fixtureName N=$repeatN → API 호출 예정: ${repeatN * 2}회 (프로덕션 경로)")

        // 방향 D: 프로덕션 경로 사용.
        // DefaultReviewService.reviewCode는 diffOptions가 null이 아니면 DiffPreprocessor →
        // reviewParallel → 파일별 buildContext(filePath=파일경로, category=FileCategoryMapper) →
        // aggregate를 거친다. 테스트는 RAG 컨텍스트를 직접 조립하지 않고 프로덕션이
        // 실제로 retrieve·compress해서 사용하는 경로 전체를 측정한다.
        val diffOptions = DiffFilterOptions()
        val filePaths = parseFilePaths(diff)
        println("[RAG] fixture 파일 경로: $filePaths")

        val runs: List<RunResult> = (1..repeatN).map { runIndex ->
            // Before: vector_store 비우고 Redis 캐시 flush → 프로덕션 내부 buildContext가
            // 빈 문자열 반환. DefaultReviewService는 이를 그대로 conventionContext로 전달한다.
            jdbcTemplate.execute("DELETE FROM vector_store")
            flushRedis()
            val reviewBefore = reviewUseCase.reviewCode(
                code = diff,
                provider = AiProvider.ANTHROPIC,
                diffOptions = diffOptions,
                mode = ReviewMode.Simple,
            )
            val contextBefore = "" // before는 vector_store 비워 있어 retrieval 없음

            // After: reindex → Redis flush(이전 run의 cache-hit 방지) → 프로덕션 경로 재호출.
            // 동일 diff이므로 cache key가 같아 flush 없으면 reviewBefore 결과가 그대로 반환된다.
            conventionIndexUseCase.reindex()
            flushRedis()
            val reviewAfter = reviewUseCase.reviewCode(
                code = diff,
                provider = AiProvider.ANTHROPIC,
                diffOptions = diffOptions,
                mode = ReviewMode.Simple,
            )

            // 진단 목적: 프로덕션 경로가 파일별로 내부에서 build한 context는 직접 관찰 불가.
            // 같은 쿼리 전략(파일명 마지막 세그먼트)으로 read-only 재호출해 길이만 샘플링한다.
            // vector_store는 여전히 populated 상태이므로 reviewAfter가 본 context와 동일한 결과를 낸다.
            // joinToString 람다는 suspend가 아니므로 for 루프로 buildContext를 호출한 뒤 조립한다.
            val perFileSections = mutableListOf<String>()
            for (fp in filePaths) {
                val query = fp.substringAfterLast("/")
                val fileContext = conventionContextService.buildContext(query = query, filePath = fp)
                perFileSections += "## $fp (query=$query, ${fileContext.length}자)\n\n$fileContext"
            }
            val contextAfter = perFileSections.joinToString("\n\n--- FILE SEPARATOR ---\n\n")

            writeRunResult(
                fixtureName = fixtureName,
                runIndex = runIndex,
                contextBefore = contextBefore,
                reviewBefore = reviewBefore,
                contextAfter = contextAfter,
                reviewAfter = reviewAfter,
            )

            buildRunResult(runIndex, reviewBefore, reviewAfter, contextAfter)
        }

        writeSummary(fixtureName, runs, repeatN)

        // 1차 검증: retrieval 인프라 — 모든 run에서 컨텍스트가 검색되어야 한다 (엄격)
        val retrievalFailMessage = "일부 run에서 RAG 컨텍스트 검색이 실패했습니다. " +
            "contextAfterLength per run: " +
            runs.joinToString { "${it.runIndex}=${it.contextAfterLength}" }
        assertThat(runs)
            .withFailMessage(retrievalFailMessage)
            .allMatch { it.contextAfterLength > 0 }

        // 2차 검증: 키워드 지표 — 중앙값 기준 (비결정성에 robust, N=1에서는 단일값)
        val beforeMedian = runs.map { it.beforeKeywordCount }.median()
        val afterMedian = runs.map { it.afterKeywordCount }.median()
        println("[RAG 품질 비교] 키워드 카운트 median: before=$beforeMedian, after=$afterMedian (N=$repeatN)")

        assertThat(afterMedian)
            .withFailMessage(
                "after 키워드 카운트 중앙값이 1 미만입니다 " +
                    "(before median=$beforeMedian, after median=$afterMedian, N=$repeatN)",
            )
            .isGreaterThanOrEqualTo(1)

        assertThat(afterMedian)
            .withFailMessage(
                "after 키워드 카운트 중앙값이 before보다 크지 않습니다 " +
                    "(before median=$beforeMedian, after median=$afterMedian, N=$repeatN)",
            )
            .isGreaterThan(beforeMedian)
    }

    // per-run 지표 집계용 불변 데이터. summary.md 표 생성과 assertion median 계산의 단일 소스.
    // severity 키는 IssueSeverity enum의 name() 값을 그대로 사용한다
    // (CRITICAL / MAJOR / MINOR / SUGGESTION).
    private data class RunResult(
        val runIndex: Int,
        val beforeScore: Int,
        val afterScore: Int,
        val beforeKeywordCount: Int,
        val afterKeywordCount: Int,
        val beforeIssueCountsBySeverity: Map<String, Int>,
        val afterIssueCountsBySeverity: Map<String, Int>,
        val contextAfterLength: Int,
    )

    // 한 iteration의 before/after 리뷰 결과로부터 RunResult를 만든다.
    // 키워드 카운팅은 companion.countKeywords (정적 regex 패턴 재사용)로 위임한다.
    private fun buildRunResult(
        runIndex: Int,
        reviewBefore: CodeReview,
        reviewAfter: CodeReview,
        contextAfter: String,
    ): RunResult = RunResult(
        runIndex = runIndex,
        beforeScore = reviewBefore.overallScore,
        afterScore = reviewAfter.overallScore,
        beforeKeywordCount = countKeywords(reviewBefore.toFullText()),
        afterKeywordCount = countKeywords(reviewAfter.toFullText()),
        beforeIssueCountsBySeverity = reviewBefore.issues
            .groupingBy { it.severity.name }
            .eachCount(),
        afterIssueCountsBySeverity = reviewAfter.issues
            .groupingBy { it.severity.name }
            .eachCount(),
        contextAfterLength = contextAfter.length,
    )

    // 정수 리스트의 중앙값. 짝수 N일 때는 하위 중앙값(정렬된 리스트의 size/2 인덱스)을 반환한다.
    // 키워드 카운트는 정수이며 비교 의미만 유지하면 되므로 Double median으로 올릴 이유가 없다.
    private fun List<Int>.median(): Int {
        require(isNotEmpty()) { "median은 비어있지 않은 리스트에서만 계산할 수 있습니다" }
        return sorted()[size / 2]
    }

    // 단일 리뷰 결과(before 또는 after)를 새 디렉토리 구조에 저장한다.
    // Spec A의 writeResult와 동일한 리포트 포맷을 유지하되 파일 경로와 헤더만 fixture/run 인식형으로 변경.
    private fun writeSingleReview(
        filename: String,
        fixtureName: String,
        runIndex: Int,
        label: String,
        context: String,
        review: CodeReview,
    ) {
        val now = LocalDateTime.now()
        File(filename).parentFile?.mkdirs()
        val fullText = review.toFullText()
        val countDetails = countByKeyword(fullText).entries.joinToString("\n") { (kw, count) ->
            "- \"$kw\": ${count}회"
        }
        File(filename).writeText(buildString {
            appendLine("# RAG 적용 $label 리뷰 결과 — $fixtureName run_$runIndex")
            appendLine()
            appendLine("## 테스트 조건")
            appendLine("- Fixture: $fixtureName")
            appendLine("- Run index: $runIndex")
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

    // 한 iteration의 before/after 결과를 run_N/ 하위에 두 개의 마크다운 파일로 저장한다.
    private fun writeRunResult(
        fixtureName: String,
        runIndex: Int,
        contextBefore: String,
        reviewBefore: CodeReview,
        contextAfter: String,
        reviewAfter: CodeReview,
    ) {
        val runDir = "plans/202604-2w/rag-quality/$fixtureName/run_$runIndex"
        writeSingleReview("$runDir/before.md", fixtureName, runIndex, "전", contextBefore, reviewBefore)
        writeSingleReview("$runDir/after.md", fixtureName, runIndex, "후", contextAfter, reviewAfter)
    }

    // 통계 표 한 행을 StringBuilder에 쓴다. min/median(하위)/max/mean 순.
    private fun StringBuilder.appendStatRow(label: String, values: List<Int>) {
        val sorted = values.sorted()
        val min = sorted.first()
        val max = sorted.last()
        val median = sorted[sorted.size / 2]
        val mean = values.average()
        appendLine("| $label | $min | $median | $max | ${"%.2f".format(mean)} |")
    }

    // N개 run의 집계 결과를 summary.md로 저장한다. N=1일 때도 자연스럽게 동작한다
    // (min=median=max=단일값, mean=단일값.00).
    private fun writeSummary(fixtureName: String, runs: List<RunResult>, repeatN: Int) {
        val filename = "plans/202604-2w/rag-quality/$fixtureName/summary.md"
        File(filename).parentFile?.mkdirs()

        val severityKeys = listOf("CRITICAL", "MAJOR", "MINOR", "SUGGESTION")

        File(filename).writeText(buildString {
            appendLine("# RAG 품질 평가 요약 — $fixtureName")
            appendLine()
            appendLine("## 실행 조건")
            appendLine("- Fixture: $fixtureName")
            appendLine("- Repeat N: $repeatN")
            appendLine("- 실행일시: ${LocalDateTime.now()}")
            appendLine()

            appendLine("## Per-run 지표")
            appendLine()
            appendLine("| Run | before 총점 | after 총점 | before 키워드 | after 키워드 | before CRITICAL | after CRITICAL | after 컨텍스트 길이 |")
            appendLine("|---|---|---|---|---|---|---|---|")
            runs.forEach { r ->
                val beforeCrit = r.beforeIssueCountsBySeverity["CRITICAL"] ?: 0
                val afterCrit = r.afterIssueCountsBySeverity["CRITICAL"] ?: 0
                appendLine("| ${r.runIndex} | ${r.beforeScore} | ${r.afterScore} | ${r.beforeKeywordCount} | ${r.afterKeywordCount} | $beforeCrit | $afterCrit | ${r.contextAfterLength} |")
            }
            appendLine()

            appendLine("## 통계 (N=$repeatN)")
            appendLine()
            appendLine("| 지표 | min | median | max | mean |")
            appendLine("|---|---|---|---|---|")
            appendStatRow("before 총점", runs.map { it.beforeScore })
            appendStatRow("after 총점", runs.map { it.afterScore })
            appendStatRow("before 키워드", runs.map { it.beforeKeywordCount })
            appendStatRow("after 키워드", runs.map { it.afterKeywordCount })
            severityKeys.forEach { sev ->
                appendStatRow("before $sev", runs.map { it.beforeIssueCountsBySeverity[sev] ?: 0 })
                appendStatRow("after $sev", runs.map { it.afterIssueCountsBySeverity[sev] ?: 0 })
            }
            appendLine()

            val beforeMedian = runs.map { it.beforeKeywordCount }.median()
            val afterMedian = runs.map { it.afterKeywordCount }.median()
            val retrievalOk = runs.all { it.contextAfterLength > 0 }
            val minKw = afterMedian >= 1
            val gtBefore = afterMedian > beforeMedian
            val verdict = if (retrievalOk && minKw && gtBefore) "PASS" else "FAIL"

            appendLine("## Assertion 결과")
            appendLine()
            appendLine("- [${if (retrievalOk) "x" else " "}] 모든 run에서 컨텍스트 검색 성공")
            appendLine("- [${if (minKw) "x" else " "}] after 키워드 중앙값 >= 1 (median=$afterMedian)")
            appendLine("- [${if (gtBefore) "x" else " "}] after 키워드 중앙값 > before 키워드 중앙값 (before=$beforeMedian, after=$afterMedian)")
            appendLine()
            appendLine("**판정:** $verdict")
        })
    }

    // 리뷰 결과 텍스트(summary + 각 이슈의 description/suggestion)를 하나의 문자열로 조립한다.
    // 키워드 카운팅과 리포트 생성이 같은 소스를 바라보도록 단일 지점으로 통일한다.
    private fun CodeReview.toFullText(): String =
        summary + "\n" + issues.joinToString("\n") { "${it.description} ${it.suggestion}" }

    // unified diff 문자열에서 `+++ b/...` 라인을 파싱하여 파일 경로 목록을 반환한다.
    // DefaultReviewService.extractFilePath와 동등한 로직이지만 테스트용 public 접근자.
    private fun parseFilePaths(diff: String): List<String> =
        diff.lineSequence()
            .filter { it.startsWith("+++ b/") }
            .map { it.removePrefix("+++ b/") }
            .toList()

    // Redis 전체 flush. DefaultReviewService가 쓰는 review 캐시를 제거해
    // 같은 diff에 대한 이전 실행 결과가 캐시 히트로 재사용되지 않게 한다.
    private fun flushRedis() {
        redisTemplate.connectionFactory
            .reactiveConnection
            .serverCommands()
            .flushAll()
            .block()
    }

}
