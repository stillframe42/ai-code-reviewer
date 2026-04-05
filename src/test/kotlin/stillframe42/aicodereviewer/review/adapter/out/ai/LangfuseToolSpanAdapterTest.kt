package stillframe42.aicodereviewer.review.adapter.out.ai

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.common.langfuse.LangfuseClient
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder
import stillframe42.aicodereviewer.config.LangfuseProperties

// LangfuseToolSpanAdapter 단위 테스트 — WireMock으로 Langfuse API 모킹
class LangfuseToolSpanAdapterTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var adapter: LangfuseToolSpanAdapter

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(options().dynamicPort()).also { it.start() }
        wireMock.stubFor(
            post(urlPathEqualTo("/api/public/ingestion"))
                .willReturn(aResponse().withStatus(200).withBody("""{"successes":[],"errors":[]}""")),
        )
        val properties = LangfuseProperties(
            host = "http://localhost:${wireMock.port()}",
            secretKey = "test-secret",
            publicKey = "test-public",
        )
        adapter = LangfuseToolSpanAdapter(LangfuseClient(properties))
        LangfuseTraceContextHolder.set("test-trace-id")
    }

    @AfterEach
    fun tearDown() {
        LangfuseTraceContextHolder.clear()
        wireMock.stop()
    }

    @Test
    fun `startSpan은 Langfuse에 span-create를 전송하고 spanId를 반환한다`() {
        val spanId = adapter.startSpan("getFileContent", mapOf("path" to "README.md"))

        assertThat(spanId).isNotEmpty()
        val requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/api/public/ingestion")))
        assertThat(requests).hasSize(1)
        val body = requests[0].bodyAsString
        assertThat(body).contains("span-create")
        assertThat(body).contains("getFileContent")
        assertThat(body).contains("test-trace-id")
    }

    @Test
    fun `endSpan은 Langfuse에 span-update를 전송한다`() {
        val spanId = "test-span-id"
        adapter.endSpan(spanId, "파일 내용입니다")

        val requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/api/public/ingestion")))
        assertThat(requests).hasSize(1)
        val body = requests[0].bodyAsString
        assertThat(body).contains("span-update")
        assertThat(body).contains(spanId)
        assertThat(body).contains("endTime")
    }

    @Test
    fun `endSpanWithError는 Langfuse에 ERROR level span-update를 전송한다`() {
        val spanId = "test-span-id"
        adapter.endSpanWithError(spanId, "GitHub API 오류")

        val requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/api/public/ingestion")))
        assertThat(requests).hasSize(1)
        val body = requests[0].bodyAsString
        assertThat(body).contains("span-update")
        assertThat(body).contains("ERROR")
        assertThat(body).contains("GitHub API 오류")
    }

    @Test
    fun `traceId가 없으면 startSpan은 빈 문자열을 반환하고 Langfuse에 전송하지 않는다`() {
        LangfuseTraceContextHolder.clear()

        val spanId = adapter.startSpan("getFileContent", emptyMap())

        assertThat(spanId).isEmpty()
        val requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/api/public/ingestion")))
        assertThat(requests).isEmpty()
    }

    @Test
    fun `spanId가 비어있으면 endSpan은 Langfuse에 전송하지 않는다`() {
        adapter.endSpan("", "output")

        val requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/api/public/ingestion")))
        assertThat(requests).isEmpty()
    }

    @Test
    fun `Langfuse 전송 실패 시 예외를 전파하지 않는다`() {
        wireMock.stop()
        // traceId는 여전히 설정되어 있음 — 전송 실패해도 예외 전파 없이 완료

        // startSpan은 spanId UUID를 생성한 뒤 ingest를 호출하므로
        // ingest 실패 시 catch에서 warn 로그 후 spanId를 반환한다
        assertThatCode { adapter.startSpan("getFileContent", emptyMap()) }.doesNotThrowAnyException()
        assertThatCode { adapter.endSpan("some-id", "output") }.doesNotThrowAnyException()
        assertThatCode { adapter.endSpanWithError("some-id", "error") }.doesNotThrowAnyException()
    }
}
