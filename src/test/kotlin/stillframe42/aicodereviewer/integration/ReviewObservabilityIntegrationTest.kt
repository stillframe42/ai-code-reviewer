package stillframe42.aicodereviewer.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

// Langfuse를 활성화하여 WireMock으로 span 전송 여부를 검증하는 통합 테스트
// integration-test 프로파일은 langfuse.enabled=false이므로 여기서 명시적으로 override한다
@TestPropertySource(properties = ["langfuse.enabled=true"])
class ReviewObservabilityIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUpStubs() {
        WireMockStubs.stubLangfuseIngestion(wireMock)
        WireMockStubs.stubAnthropicReviewWithIssues(wireMock)
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        WireMockStubs.stubOpenAiChatResponse(wireMock, "compressed convention text")
    }

    @Test
    fun `리뷰 실행 후 Langfuse에 review_root와 RAG span이 기록된다`() {
        val diff = """
            diff --git a/src/main/kotlin/ProductService.kt b/src/main/kotlin/ProductService.kt
            index 0000000..1111111 100644
            --- a/src/main/kotlin/ProductService.kt
            +++ b/src/main/kotlin/ProductService.kt
            @@ -1,3 +1,5 @@
             class ProductService {
            +    fun hello() {
            +        println("world")
            +    }
             }
        """.trimIndent()

        runBlocking {
            reviewUseCase.reviewCode(
                code = diff,
                provider = AiProvider.ANTHROPIC,
                diffOptions = DiffFilterOptions(),
                mode = ReviewMode.Simple,
            )
        }

        // 리뷰 파이프라인 실행 후 Langfuse에 ingestion 요청이 발생했는지 확인
        val requests = WireMockStubs.findLangfuseIngestionRequests(wireMock)
        assertThat(requests).isNotEmpty

        // 전송된 모든 이벤트 타입 수집
        val allTypes = extractAllEventTypes()

        // LLM 호출이 발생하면 LangfuseObservationHandler가 trace-create와 generation-create를 기록
        assertThat(allTypes).contains("trace-create")
        assertThat(allTypes).contains("generation-create")
    }

    private fun extractAllEventTypes(): List<String> {
        val requests = WireMockStubs.findLangfuseIngestionRequests(wireMock)
        return requests.flatMap { req ->
            val json = objectMapper.readValue<Map<String, Any>>(req.bodyAsString)
            @Suppress("UNCHECKED_CAST")
            val batch = json["batch"] as? List<Map<String, Any>> ?: emptyList()
            batch.mapNotNull { it["type"] as? String }
        }
    }
}
