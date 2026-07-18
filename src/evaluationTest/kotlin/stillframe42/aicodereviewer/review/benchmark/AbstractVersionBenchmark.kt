package stillframe42.aicodereviewer.review.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import stillframe42.aicodereviewer.common.TokenEstimator
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.domain.model.IssueCategory
import stillframe42.aicodereviewer.review.domain.model.IssueSeverity
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import kotlin.system.measureTimeMillis

// 프롬프트 버전별 벤치마크 공통 로직 — 픽스처 로더 + 테스트 케이스 + 결과 출력
// PostgreSQL Testcontainers 공유 — AbstractIntegrationTest의 싱글톤 컨테이너를 재사용한다
@SpringBootTest
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractVersionBenchmark {

    companion object {
        // AbstractIntegrationTest의 싱글톤 컨테이너를 참조하여 중복 기동 방지
        @JvmStatic
        @DynamicPropertySource
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
            val pg = AbstractIntegrationTest.postgres
            val wm = AbstractIntegrationTest.wireMock
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { pg.username }
            registry.add("spring.datasource.password") { pg.password }
            registry.add("spring.ai.anthropic.base-url") { "http://localhost:${wm.port()}" }
            // openai-java SDK 는 base-url 에 /v1 이 포함되는 규약 — 스텁 경로(/v1/*)와 정렬
            registry.add("spring.ai.openai.base-url") { "http://localhost:${wm.port()}/v1" }
        }
    }

    // 각 버전 클래스에서 오버라이드
    abstract val version: String

    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    // 현재 활성화된 시스템 프롬프트 리소스 (버전별로 @TestPropertySource 오버라이드됨)
    @Value("\${app.prompt.review-system}")
    private lateinit var systemPromptResource: Resource

    // 버전별 결과 누적 리스트
    private val results = mutableListOf<PromptBenchmarkResult>()

    // API 키 사용 가능 여부 확인
    private fun isApiKeyAvailable(): Boolean {
        val key = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        return key != null && key.isNotBlank() && key != "test-dummy-key"
    }

    // 클래스패스에서 픽스처 파일 로드
    private fun loadFixture(filename: String): String {
        val resource = javaClass.getResourceAsStream("/fixtures/review/$filename")
            ?: error("픽스처 파일을 찾을 수 없습니다: /fixtures/review/$filename")
        return resource.bufferedReader().readText()
    }

    // 시스템 프롬프트 토큰 추정 (파일이 로드 불가면 0 반환)
    private fun estimateSystemPromptTokens(): Int = runCatching {
        TokenEstimator.estimate(systemPromptResource.getContentAsString(Charsets.UTF_8))
    }.getOrDefault(0)

    // 리뷰 호출 후 PromptBenchmarkResult로 변환하여 누적
    private fun runBenchmark(fixtureName: String): PromptBenchmarkResult {
        assumeTrue(isApiKeyAvailable(), "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다")

        val code = loadFixture(fixtureName)
        var review = reviewUseCase.reviewCode(code, AiProvider.ANTHROPIC)
        var durationMs = 0L

        // 실제 측정은 measureTimeMillis로 재실행
        durationMs = measureTimeMillis {
            review = reviewUseCase.reviewCode(code, AiProvider.ANTHROPIC)
        }

        val issuesByCategory = review.issues
            .groupBy { it.category }
            .mapValues { (_, issues) -> issues.size }

        val issuesBySeverity = review.issues
            .groupBy { it.severity }
            .mapValues { (_, issues) -> issues.size }

        val allIssueFieldsPopulated = review.issues.all { issue ->
            issue.id.isNotBlank() &&
                issue.description.isNotBlank() &&
                issue.suggestion.isNotBlank()
        }

        val result = PromptBenchmarkResult(
            version = version,
            fixtureName = fixtureName.removeSuffix(".kt"),
            overallScore = review.overallScore,
            issuesByCategory = issuesByCategory,
            issuesBySeverity = issuesBySeverity,
            positiveCount = review.positives.size,
            systemPromptTokenEstimate = estimateSystemPromptTokens(),
            durationMs = durationMs,
            allIssueFieldsPopulated = allIssueFieldsPopulated,
        )
        results.add(result)
        BenchmarkResultStore.addResult(result)
        return result
    }

    // ── 픽스처별 테스트 메서드 ──────────────────────────────────────

    @Test
    fun `security-sql-injection 픽스처 리뷰`() {
        val result = runBenchmark("security-sql-injection.kt")
        // SQL Injection: SECURITY 이슈 존재 + CRITICAL 감지 + 낮은 점수
        assertThat(result.issuesByCategory[IssueCategory.SECURITY] ?: 0)
            .`as`("SECURITY 이슈가 최소 1개 이상이어야 합니다")
            .isGreaterThanOrEqualTo(1)
        assertThat(result.overallScore).`as`("점수는 1-6 범위여야 합니다").isBetween(1, 6)
    }

    @Test
    fun `security-hardcoded-credentials 픽스처 리뷰`() {
        val result = runBenchmark("security-hardcoded-credentials.kt")
        assertThat(result.issuesByCategory[IssueCategory.SECURITY] ?: 0)
            .`as`("SECURITY 이슈가 최소 1개 이상이어야 합니다")
            .isGreaterThanOrEqualTo(1)
        assertThat(result.overallScore).`as`("점수는 1-7 범위여야 합니다").isBetween(1, 7)
    }

    @Test
    fun `performance-n-plus-one 픽스처 리뷰`() {
        val result = runBenchmark("performance-n-plus-one.kt")
        assertThat(result.issuesByCategory[IssueCategory.PERFORMANCE] ?: 0)
            .`as`("PERFORMANCE 이슈가 최소 1개 이상이어야 합니다")
            .isGreaterThanOrEqualTo(1)
        assertThat(result.overallScore).`as`("점수는 1-8 범위여야 합니다").isBetween(1, 8)
    }

    @Test
    fun `performance-inefficient-loop 픽스처 리뷰`() {
        val result = runBenchmark("performance-inefficient-loop.kt")
        assertThat(result.issuesByCategory[IssueCategory.PERFORMANCE] ?: 0)
            .`as`("PERFORMANCE 이슈가 최소 1개 이상이어야 합니다")
            .isGreaterThanOrEqualTo(1)
        assertThat(result.overallScore).`as`("점수는 1-8 범위여야 합니다").isBetween(1, 8)
    }

    @Test
    fun `readability-magic-numbers 픽스처 리뷰`() {
        val result = runBenchmark("readability-magic-numbers.kt")
        assertThat(result.issuesByCategory[IssueCategory.READABILITY] ?: 0)
            .`as`("READABILITY 이슈가 최소 2개 이상이어야 합니다")
            .isGreaterThanOrEqualTo(2)
        assertThat(result.overallScore).`as`("점수는 1-8 범위여야 합니다").isBetween(1, 8)
    }

    @Test
    fun `architecture-spr-violation 픽스처 리뷰`() {
        val result = runBenchmark("architecture-spr-violation.kt")
        assertThat(result.issuesByCategory[IssueCategory.ARCHITECTURE] ?: 0)
            .`as`("ARCHITECTURE 이슈가 최소 1개 이상이어야 합니다")
            .isGreaterThanOrEqualTo(1)
        assertThat(result.overallScore).`as`("점수는 1-7 범위여야 합니다").isBetween(1, 7)
    }

    @Test
    fun `clean-simple-function 픽스처 리뷰 (false positive 측정)`() {
        val result = runBenchmark("clean-simple-function.kt")
        // 깨끗한 코드: CRITICAL/MAJOR 이슈가 없어야 함
        val criticalCount = result.issuesBySeverity[IssueSeverity.CRITICAL] ?: 0
        val majorCount = result.issuesBySeverity[IssueSeverity.MAJOR] ?: 0
        assertThat(criticalCount + majorCount)
            .`as`("깨끗한 코드에서 CRITICAL/MAJOR 이슈가 없어야 합니다")
            .isEqualTo(0)
        assertThat(result.overallScore).`as`("점수는 7-10 범위여야 합니다").isBetween(7, 10)
    }

    // ── 결과 출력 ──────────────────────────────────────────────────

    @AfterAll
    fun printBenchmarkSummary() {
        BenchmarkResultStore.markVersionCompleted(version)

        // 모든 버전이 완료된 시점에 한 번만 통합 표 출력
        if (BenchmarkResultStore.isLastVersion()) {
            BenchmarkResultStore.printCombinedSummary()
        }
    }
}
