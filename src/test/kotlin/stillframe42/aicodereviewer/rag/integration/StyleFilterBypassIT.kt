package stillframe42.aicodereviewer.rag.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

// styleFilterBypass=true 일 때 STYLE 카테고리 쿼리가 다른 카테고리 청크도 포함하는지 검증.
@TestPropertySource(properties = ["app.rag.style-filter-bypass=true"])
class StyleFilterBypassIT : AbstractIntegrationTest() {

    @Autowired
    private lateinit var hybridSearchService: HybridConventionSearchService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @BeforeEach
    fun stubEmbedding() {
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("openai-embedding-batch")
                )
        )
    }

    @Test
    fun `bypass=true 시 STYLE 쿼리 호출이 정상 응답한다`(): Unit = runBlocking {
        conventionIndexUseCase.reindex()

        // bypass=true 분기로 effectiveCategory=null 이 되어 카테고리 필터 없이 검색됨.
        // WireMock 임베딩 stub 한계(모든 임베딩 동일 벡터 0.1f)로 실제 의미 매칭 효과는 검증 불가 —
        // 본 테스트는 코드 경로의 회귀 방지 smoke test 역할. 실제 효과는 real OpenAI 측정에서 검증.
        val results = hybridSearchService.searchRaw(
            query = "ReportFormatter.kt",
            topK = 10,
            category = ConventionCategory.STYLE,
        )

        assertThat(results).isNotEmpty
    }
}
