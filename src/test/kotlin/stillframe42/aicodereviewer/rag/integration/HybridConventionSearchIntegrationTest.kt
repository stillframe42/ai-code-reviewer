package stillframe42.aicodereviewer.rag.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

class HybridConventionSearchIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var hybridSearchService: HybridConventionSearchService

    @Autowired
    lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM vector_store")
        // 벡터 검색이 쿼리를 임베딩할 때 호출하는 OpenAI Embedding API 스텁
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withTransformers("openai-embedding-batch"),
                ),
        )
        // LlmContextCompressorAdapter가 호출하는 OpenAI Chat API 스텁 (압축 정상 경로)
        WireMockStubs.stubOpenAiChatResponse(wireMock, body = "compressed test content")
    }

    @Test
    fun `빈 DB에서 검색하면 빈 결과를 반환한다`() {
        val results = hybridSearchService.search("OWASP injection")

        assertThat(results).isEmpty()
    }

    @Test
    fun `category SECURITY 필터 적용 시 반환 문서가 모두 SECURITY 카테고리다`() {
        conventionIndexUseCase.reindex()

        val results = hybridSearchService.search("OWASP injection", topK = 5, category = ConventionCategory.SECURITY)

        assertThat(results).isNotEmpty()
        assertThat(results).allSatisfy { doc ->
            assertThat(doc.metadata["category"]).isEqualTo("SECURITY")
        }
    }
}
