package stillframe42.aicodereviewer.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

        reviewUseCase.reviewCode(
            code = diff,
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
            mode = ReviewMode.Simple,
        )

        val allEvents = extractAllEvents()
        val eventTypes = allEvents.map { it["type"] as? String }
        val spanNames = extractSpanNames(allEvents)

        // trace 자동 생성 확인
        assertThat(eventTypes).contains("trace-create")
        // LLM 호출에 의한 generation 이벤트 확인
        assertThat(eventTypes).contains("generation-create")
        // review.root span 생성 확인
        assertThat(spanNames).contains("review.root")
        // RAG 파이프라인 span 확인
        assertThat(spanNames).contains("rag.context")
        assertThat(spanNames).contains("rag.hybrid-search")
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractAllEvents(): List<Map<String, Any>> {
        val requests = WireMockStubs.findLangfuseIngestionRequests(wireMock)
        return requests.flatMap { req ->
            val json = objectMapper.readValue<Map<String, Any>>(req.bodyAsString)
            json["batch"] as? List<Map<String, Any>> ?: emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractSpanNames(events: List<Map<String, Any>>): List<String> =
        events.filter { it["type"] == "span-create" }
            .mapNotNull { event ->
                (event["body"] as? Map<String, Any>)?.get("name") as? String
            }
}
