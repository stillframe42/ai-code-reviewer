package stillframe42.aicodereviewer.rag

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService

class HybridConventionSearchIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var hybridSearchService: HybridConventionSearchService

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
    }

    @Test
    fun `빈 DB에서 검색하면 빈 결과를 반환한다`() {
        val results = runBlocking { hybridSearchService.search("OWASP injection") }

        assertThat(results).isEmpty()
    }
}
