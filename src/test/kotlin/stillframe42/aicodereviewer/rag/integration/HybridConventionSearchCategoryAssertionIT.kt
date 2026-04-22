package stillframe42.aicodereviewer.rag.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper

// 의도된 위반 fixture 의 파일 경로가 FileCategoryMapper 로 예상 카테고리에 매핑되고,
// HybridConventionSearchService.searchRaw 결과가 모두 해당 카테고리에서 반환되는지 검증.
// - 압축 우회 경로(searchRaw)만 테스트한다 (검색 품질 자체 검증이 목적).
// - RRF 병합 결과를 디버깅용으로 로그 출력한다.
class HybridConventionSearchCategoryAssertionIT : AbstractIntegrationTest() {

    private val logger = LoggerFactory.getLogger(HybridConventionSearchCategoryAssertionIT::class.java)

    @Autowired
    lateinit var hybridSearchService: HybridConventionSearchService

    @Autowired
    lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM vector_store")
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withTransformers("openai-embedding-batch"),
                ),
        )
        WireMockStubs.stubOpenAiChatResponse(wireMock, body = "compressed test content")
        runBlocking { conventionIndexUseCase.reindex() }
    }

    @Test
    fun `SECURITY fixture - 파일 경로는 SECURITY로 매핑되고 top-3 검색 결과는 모두 SECURITY 카테고리다`() {
        val patch = loadFixture("security-sql-injection.patch")
        val filePath = parseFirstFilePath(patch)
        val category = FileCategoryMapper.selectCategory(filePath)

        assertThat(category).isEqualTo(ConventionCategory.SECURITY)

        val query = "SecurityAuditRepository SQL injection"
        val results = runBlocking {
            hybridSearchService.searchRaw(query, topK = 3, category = category)
        }

        logRrfResults("SECURITY", query, results)

        assertThat(results).isNotEmpty()
        assertThat(results).allSatisfy { doc ->
            assertThat(doc.metadata["category"]).isEqualTo("SECURITY")
        }
    }

    @Test
    fun `ARCH fixture - 파일 경로는 ARCH로 매핑되고 top-3 검색 결과는 모두 ARCH 카테고리다`() {
        val patch = loadFixture("arch-jpa-entity-dataclass.patch")
        val filePath = parseFirstFilePath(patch)
        val category = FileCategoryMapper.selectCategory(filePath)

        assertThat(category).isEqualTo(ConventionCategory.ARCH)

        val query = "OrderEntity JPA data class"
        val results = runBlocking {
            hybridSearchService.searchRaw(query, topK = 3, category = category)
        }

        logRrfResults("ARCH", query, results)

        assertThat(results).isNotEmpty()
        assertThat(results).allSatisfy { doc ->
            assertThat(doc.metadata["category"]).isEqualTo("ARCH")
        }
    }

    @Test
    fun `STYLE fixture - 파일 경로는 STYLE로 매핑되고 top-3 검색 결과는 모두 STYLE 카테고리다`() {
        val patch = loadFixture("style-long-function.patch")
        val filePath = parseFirstFilePath(patch)
        val category = FileCategoryMapper.selectCategory(filePath)

        assertThat(category).isEqualTo(ConventionCategory.STYLE)

        val query = "long function local function FQCN"
        val results = runBlocking {
            hybridSearchService.searchRaw(query, topK = 3, category = category)
        }

        logRrfResults("STYLE", query, results)

        assertThat(results).isNotEmpty()
        assertThat(results).allSatisfy { doc ->
            assertThat(doc.metadata["category"]).isEqualTo("STYLE")
        }
    }

    // unified diff 문자열에서 `+++ b/...` 라인을 파싱하여 첫 번째 파일 경로를 반환한다.
    private fun parseFirstFilePath(diff: String): String =
        diff.lineSequence()
            .filter { it.startsWith("+++ b/") }
            .map { it.removePrefix("+++ b/") }
            .firstOrNull()
            ?: error("patch에서 파일 경로를 찾을 수 없습니다")

    // fixture basename으로 src/test/resources/fixtures/review/ 하위 파일을 로드한다.
    private fun loadFixture(name: String): String {
        val stream = this::class.java.getResourceAsStream("/fixtures/review/$name")
            ?: error("fixture 파일을 찾을 수 없습니다: $name")
        return stream.bufferedReader().readText()
    }

    // RRF 병합 결과를 디버깅용 로그로 출력한다. top-3 각 문서의 source, category, score를 기록한다.
    private fun logRrfResults(label: String, query: String, results: List<Document>) {
        logger.info("[{}] query='{}' → {} results", label, query, results.size)
        results.forEachIndexed { index, doc ->
            logger.info(
                "  rank={} source={} category={} textPreview={}",
                index + 1,
                doc.metadata["source"],
                doc.metadata["category"],
                doc.text?.take(80)?.replace("\n", " "),
            )
        }
    }
}
