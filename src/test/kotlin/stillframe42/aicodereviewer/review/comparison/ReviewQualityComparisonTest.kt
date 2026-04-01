package stillframe42.aicodereviewer.review.comparison

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tools.jackson.databind.ObjectMapper
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import stillframe42.aicodereviewer.common.TokenEstimator
import stillframe42.aicodereviewer.github.domain.port.out.GitHubApiPort
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewModeRequest
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

// WITHOUT_TOOLS vs WITH_TOOLS 모드 품질 비교 E2E 통합 테스트
// 실행 조건: ANTHROPIC_API_KEY, GITHUB_APP_ID, GITHUB_INSTALLATION_ID,
//            GITHUB_TEST_REPO, GITHUB_TEST_PR_NUMBER 환경변수 설정 필요
// 미설정 시 assumeTrue에 의해 자동 스킵됨
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewQualityComparisonTest {

    companion object {
        // 테스트 application.yml은 application-secret.yml을 로드하지 않으므로
        // @DynamicPropertySource로 런타임에 GITHUB_APP_ID env var를 Spring 프로퍼티에 주입한다
        // (GitHubAppJwtGeneratorTest와 동일한 패턴)
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            val appId = System.getenv("GITHUB_APP_ID") ?: "0"
            registry.add("github.app.app-id") { appId }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var gitHubApiPort: GitHubApiPort

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var client: RestTestClient
    private lateinit var credentials: GitHubTestCredentials.Credentials
    private lateinit var diff: String

    // @AfterAll에서 보고서 생성에 사용 — null이면 해당 모드 실행 실패로 보고서 생략
    private var simpleResult: ComparisonResult? = null
    private var toolsResult: ComparisonResult? = null

    @BeforeAll
    fun setUp() {
        // 실제 GitHub + Anthropic 자격증명 검증 (미설정 시 테스트 클래스 전체 스킵)
        credentials = GitHubTestCredentials.assumeFullCredentials()
        client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        diff = runBlocking {
            withTimeout(30.seconds) {
                gitHubApiPort.getPrDiff(
                    credentials.repo,
                    credentials.prNumber,
                    credentials.installationId,
                )
            }
        }
    }

    @Test
    fun `WITHOUT_TOOLS 모드로 리뷰를 실행하고 결과를 캡처한다`() {
        val requestBody = objectMapper.writeValueAsString(
            mapOf("code" to diff, "reviewMode" to ReviewModeRequest.WITHOUT_TOOLS.name),
        )

        var review: CodeReview? = null
        val latencyMs = measureTimeMillis {
            review = client.post().uri("/api/review")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange()
                .expectStatus().isOk
                .expectBody(CodeReview::class.java)
                .returnResult().responseBody!!
        }

        simpleResult = ComparisonResult(
            mode = ReviewModeRequest.WITHOUT_TOOLS,
            review = review!!,
            latencyMs = latencyMs,
            estimatedOutputTokens = estimateOutputTokens(review!!),
        )
    }

    @Test
    fun `WITH_TOOLS 모드로 리뷰를 실행하고 결과를 캡처한다`() {
        val requestBody = objectMapper.writeValueAsString(
            mapOf(
                "code" to diff,
                "reviewMode" to ReviewModeRequest.WITH_TOOLS.name,
                "installationId" to credentials.installationId,
            ),
        )

        var review: CodeReview? = null
        val latencyMs = measureTimeMillis {
            review = client.post().uri("/api/review")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange()
                .expectStatus().isOk
                .expectBody(CodeReview::class.java)
                .returnResult().responseBody!!
        }

        toolsResult = ComparisonResult(
            mode = ReviewModeRequest.WITH_TOOLS,
            review = review!!,
            latencyMs = latencyMs,
            estimatedOutputTokens = estimateOutputTokens(review!!),
        )
    }

    // 두 테스트가 모두 완료된 후 비교 보고서 생성
    @AfterAll
    fun generateComparisonReport() {
        val simple = simpleResult ?: return
        val tools = toolsResult ?: return
        val report = ComparisonReportWriter.generate(simple, tools, credentials.repo, credentials.prNumber)
        val outputPath = Paths.get("plans/202603-3w/review_quality_comparison.md")
        ComparisonReportWriter.writeTo(outputPath, report)
        println("\n[비교 보고서 생성 완료] $outputPath\n")
    }

    // 응답 텍스트 기반 출력 토큰 추정 (4자 ≈ 1토큰)
    private fun estimateOutputTokens(review: CodeReview): Int {
        val text = review.summary +
            review.issues.joinToString { it.description + " " + it.suggestion } +
            review.positives.joinToString()
        return TokenEstimator.estimate(text)
    }
}
