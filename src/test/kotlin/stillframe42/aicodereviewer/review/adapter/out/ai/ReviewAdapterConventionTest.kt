package stillframe42.aicodereviewer.review.adapter.out.ai

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.notContaining
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// ReviewAdapter convention 주입 및 format 스키마 주입 통합 테스트
class ReviewAdapterConventionTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var aiReviewPort: AiReviewPort

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubAnthropicReview(wireMock)
    }

    @Test
    fun `format 스키마가 Anthropic 요청 본문에 포함된다`() {
        aiReviewPort.reviewCode(
            code = "fun foo() {}",
            provider = AiProvider.ANTHROPIC,
        )

        // BeanOutputConverter.getFormat()이 주입되면 overall_score 필드 설명이 system 메시지에 포함된다
        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("overall_score"))
        )
    }

    @Test
    fun `conventionContext가 제공되면 MAJOR 위반 지시문이 요청 본문에 포함된다`() {
        aiReviewPort.reviewCode(
            code = "fun foo() {}",
            provider = AiProvider.ANTHROPIC,
            conventionContext = "Controller는 UseCase 인터페이스에만 의존한다.",
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("MAJOR"))
        )
    }

    @Test
    fun `conventionContext가 null이면 convention 지시문 없이 format은 포함된다`() {
        aiReviewPort.reviewCode(
            code = "fun foo() {}",
            provider = AiProvider.ANTHROPIC,
            conventionContext = null,
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("overall_score"))
                .withRequestBody(notContaining("severity MAJOR 이슈로"))
                .withRequestBody(notContaining("참고 컨벤션 문서"))
        )
    }
}
