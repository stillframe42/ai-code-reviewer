package stillframe42.aicodereviewer.review.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// Spring AI 2.0 GA(RC2 #6336)의 옵션 "교체" 의미론 회귀 방지 계약 —
// 런타임 .options() 는 default options 와 병합되지 않으므로, critical-model
// 오버라이드 호출에서도 yml 의 max-tokens/temperature 가 요청 페이로드에
// 유지되는지 실제 HTTP 요청 본문으로 고정한다.
class ReviewAdapterModelOverrideIT : AbstractIntegrationTest() {

    @Autowired
    private lateinit var aiReviewPort: AiReviewPort

    private fun capturedAnthropicRequestBody() =
        ObjectMapper().readTree(
            wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/messages"))).single().bodyAsString,
        )

    @Test
    fun `critical-model 오버라이드 시에도 yml 의 max-tokens 와 temperature 가 요청에 유지된다`() {
        WireMockStubs.stubAnthropicReview(wireMock)

        runBlocking {
            aiReviewPort.reviewCode(
                code = "fun foo() = 1",
                provider = AiProvider.ANTHROPIC,
                mode = ReviewMode.Simple,
                reviewContext = null,
                modelName = "claude-sonnet-4-6",
                conventionContext = null,
            )
        }

        val body = capturedAnthropicRequestBody()
        assertThat(body["model"].asText()).isEqualTo("claude-sonnet-4-6")
        assertThat(body["max_tokens"]?.asInt()).isEqualTo(4096)
        assertThat(body["temperature"]?.asDouble()).isEqualTo(0.7)
    }

    @Test
    fun `기본 경로(오버라이드 없음)는 yml 의 model·max-tokens·temperature 를 그대로 사용한다`() {
        WireMockStubs.stubAnthropicReview(wireMock)

        runBlocking {
            aiReviewPort.reviewCode(
                code = "fun foo() = 1",
                provider = AiProvider.ANTHROPIC,
                mode = ReviewMode.Simple,
                reviewContext = null,
                modelName = null,
                conventionContext = null,
            )
        }

        val body = capturedAnthropicRequestBody()
        assertThat(body["model"].asText()).isEqualTo("claude-haiku-4-5-20251001")
        assertThat(body["max_tokens"]?.asInt()).isEqualTo(4096)
        assertThat(body["temperature"]?.asDouble()).isEqualTo(0.7)
    }
}
