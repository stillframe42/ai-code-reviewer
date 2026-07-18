package stillframe42.aicodereviewer.rag.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

// search()/searchRaw()에 threshold 파라미터를 명시 전달할 때 호출 단위로 동작하는지 검증.
// WireMock 임베딩 stub로 모든 임베딩이 동일하므로 의미적 차이는 검증 불가 —
// 본 테스트는 파라미터 전파 + 호출 안전성 smoke test 역할.
class HybridSearchThresholdParamIT : AbstractIntegrationTest() {

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
    fun `threshold 파라미터를 명시 전달해도 정상 응답한다`() {
        conventionIndexUseCase.reindex()

        val loose = hybridSearchService.searchRaw(
            "OrderService.kt", topK = 10, category = null, threshold = 0.0
        )
        val strict = hybridSearchService.searchRaw(
            "OrderService.kt", topK = 10, category = null, threshold = 0.8
        )

        // strict는 loose 이하 (WireMock 환경에선 동일 가능, 의미적 분리는 실 API에서 검증)
        assertThat(strict.size).isLessThanOrEqualTo(loose.size)
    }
}
