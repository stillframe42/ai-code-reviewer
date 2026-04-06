package stillframe42.aicodereviewer.review.adapter.out.ai

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// SpringAiReviewAdapter 모델 오버라이드 통합 테스트
// modelName이 지정됐을 때 Anthropic API 요청에 해당 모델이 포함되는지 검증한다
class SpringAiReviewAdapterModelTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var aiReviewPort: AiReviewPort

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubAnthropicReview(wireMock)
    }

    @Test
    fun `modelName이 지정되면 Anthropic API 요청에 해당 모델이 사용된다`() = runBlocking {
        aiReviewPort.reviewCode(
            code = "fun foo() {}",
            provider = AiProvider.ANTHROPIC,
            modelName = "claude-sonnet-4-6",
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("\"model\":\"claude-sonnet-4-6\""))
        )
        Unit
    }

    @Test
    fun `modelName이 null이면 기본 설정 모델이 사용된다`() = runBlocking {
        aiReviewPort.reviewCode(
            code = "fun foo() {}",
            provider = AiProvider.ANTHROPIC,
            modelName = null,
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("\"model\":\"claude-haiku-4-5-20251001\""))
        )
        Unit
    }
}
