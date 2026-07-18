package stillframe42.aicodereviewer.rag.adapter.out.ai

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import stillframe42.aicodereviewer.rag.domain.port.out.ContextCompressorPort
import java.util.UUID

// LlmContextCompressorAdapter 통합 테스트 — WireMock으로 OpenAI 호출 모킹
// 임계값 우회, 압축, NONE/빈 응답, 실패 fallback, 혼합 케이스를 검증한다
class LlmContextCompressorAdapterTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var compressor: ContextCompressorPort

    @BeforeEach
    fun resetStubs() {
        wireMock.resetAll()
    }

    private fun docOfSize(charCount: Int, source: String = "test.md"): RagDocument {
        // 한글 1자 ≈ 1 토큰 가정. 임계값 300 토큰 초과시키려면 약 300+자 필요.
        // 안전하게 600자 사용 (cl100k_base에서 약 400~500 토큰)
        val text = "헥사고날 아키텍처 컨벤션 ".repeat(charCount / 14 + 1).take(charCount)
        return RagDocument(
            id = UUID.randomUUID().toString(),
            text = text,
            metadata = mapOf("source" to source),
        )
    }

    private fun smallDoc(): RagDocument =
        RagDocument(
            id = UUID.randomUUID().toString(),
            text = "짧은 청크",  // 5자 → 매우 작음, 임계값 우회
            metadata = mapOf("source" to "small.md"),
        )

    @Test
    fun `임계값 미만 청크는 LLM 호출 없이 원본 통과`() {
        // OpenAI stub 등록 안 함 — 호출되면 안 됨
        val docs = listOf(smallDoc(), smallDoc())

        val result = compressor.compress("query", docs)

        assertThat(result).hasSize(2)
        assertThat(result[0].text).isEqualTo("짧은 청크")
        wireMock.verify(exactly(0), postRequestedFor(urlPathEqualTo("/v1/chat/completions")))
    }

    @Test
    fun `임계값 초과 청크는 LLM 호출 후 압축 결과 반환`() {
        WireMockStubs.stubOpenAiChatResponse(wireMock, body = "발췌된 핵심 내용")
        val largeDoc = docOfSize(800)

        val result = compressor.compress("query", listOf(largeDoc))

        assertThat(result).hasSize(1)
        assertThat(result[0].text).isEqualTo("발췌된 핵심 내용")
        assertThat(result[0].metadata["source"]).isEqualTo("test.md")
        wireMock.verify(exactly(1), postRequestedFor(urlPathEqualTo("/v1/chat/completions")))
    }

    @Test
    fun `LLM이 NONE을 반환하면 청크 제외`() {
        WireMockStubs.stubOpenAiChatResponse(wireMock, body = "NONE")
        val largeDoc = docOfSize(800)

        val result = compressor.compress("query", listOf(largeDoc))

        assertThat(result).isEmpty()
    }

    @Test
    fun `LLM이 빈 문자열을 반환하면 청크 제외`() {
        WireMockStubs.stubOpenAiChatResponse(wireMock, body = "")
        val largeDoc = docOfSize(800)

        val result = compressor.compress("query", listOf(largeDoc))

        assertThat(result).isEmpty()
    }

    @Test
    fun `LLM 호출 실패 시 원본 청크로 fallback`() {
        WireMockStubs.stubOpenAiChatError(wireMock)
        val largeDoc = docOfSize(800)
        val originalText = largeDoc.text

        val result = compressor.compress("query", listOf(largeDoc))

        assertThat(result).hasSize(1)
        assertThat(result[0].text).isEqualTo(originalText)
    }

    @Test
    fun `압축 프롬프트에 쿼리 텍스트가 포함된다`() {
        WireMockStubs.stubOpenAiChatResponse(wireMock, body = "발췌")
        val largeDoc = docOfSize(800)

        compressor.compress("OrderService.kt", listOf(largeDoc))

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/chat/completions"))
                .withRequestBody(containing("OrderService.kt"))
        )
    }
}
