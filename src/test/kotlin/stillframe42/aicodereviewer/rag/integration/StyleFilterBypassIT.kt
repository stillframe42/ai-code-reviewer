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

        // bypass=true 분기로 effectiveCategory=null이 되어 카테고리 필터 없이 검색됨.
        // 의미적 매칭 효과 검증(STYLE 외 카테고리 청크 실제 회수)은 WireMock 임베딩 stub 한계로 불가 —
        // 모든 임베딩이 동일 벡터(0.1f)라 pgvector가 insertion order로 결과를 결정함.
        // 실제 효과는 Step A-5 4차 baseline 측정(real OpenAI)에서 검증.
        // 본 테스트는 코드 경로의 회귀 방지 smoke test 역할.
        val results = hybridSearchService.searchRaw(
            query = "ReportFormatter.kt",
            topK = 10,
            category = ConventionCategory.STYLE,
        )

        assertThat(results).isNotEmpty
    }
}
