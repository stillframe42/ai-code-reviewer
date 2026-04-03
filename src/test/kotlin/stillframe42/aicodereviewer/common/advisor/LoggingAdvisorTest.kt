package stillframe42.aicodereviewer.common.advisor

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class LoggingAdvisorTest {

    private val loggingAdvisor = LoggingAdvisor()

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    // Logback ListAppender로 LoggingAdvisor 로그를 캡처
    @BeforeEach
    fun setUpLogger() {
        logger = LoggerFactory.getLogger(LoggingAdvisor::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    @AfterEach
    fun tearDownLogger() {
        logger.detachAppender(appender)
    }

    // ChatClientRequest를 최소한으로 mock — instructions를 빈 리스트로 반환
    private fun mockRequest(): ChatClientRequest {
        val request = mock(ChatClientRequest::class.java)
        val prompt = mock(Prompt::class.java)
        `when`(request.prompt()).thenReturn(prompt)
        `when`(prompt.instructions).thenReturn(emptyList())
        return request
    }

    // metadata.usage가 있는 ChatClientResponse mock
    private fun mockResponseWithUsage(
        model: String = "claude-3-5-sonnet",
        promptTokens: Int = 100,
        completionTokens: Int = 50,
    ): ChatClientResponse {
        val response = mock(ChatClientResponse::class.java)
        val chatResponse = mock(ChatResponse::class.java)
        val metadata = mock(ChatResponseMetadata::class.java)
        val usage = mock(Usage::class.java)

        `when`(response.chatResponse()).thenReturn(chatResponse)
        `when`(chatResponse.metadata).thenReturn(metadata)
        `when`(metadata.model).thenReturn(model)
        `when`(metadata.usage).thenReturn(usage)
        `when`(usage.promptTokens).thenReturn(promptTokens)
        `when`(usage.completionTokens).thenReturn(completionTokens)
        return response
    }

    // chatResponse()가 null인 ChatClientResponse mock (usage fallback 검증용)
    private fun mockResponseWithoutUsage(): ChatClientResponse {
        val response = mock(ChatClientResponse::class.java)
        `when`(response.chatResponse()).thenReturn(null)
        return response
    }

    @Test
    fun `sync 호출 시 chain의 응답을 그대로 반환한다`() {
        val request = mockRequest()
        val expectedResponse = mockResponseWithUsage()
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(expectedResponse)

        val result = loggingAdvisor.adviseCall(request, chain)

        assertThat(result).isSameAs(expectedResponse)
        verify(chain).nextCall(request)
    }

    @Test
    fun `sync 호출 시 metadata usage가 있으면 실제 토큰 수를 로그에 기록한다`() {
        val request = mockRequest()
        val response = mockResponseWithUsage(
            model = "claude-3-5-sonnet",
            promptTokens = 1024,
            completionTokens = 256,
        )
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(response)

        loggingAdvisor.adviseCall(request, chain)

        val logs = appender.list.map { it.formattedMessage }
        assertThat(logs).anyMatch { it.contains("prompt=1024tok") && it.contains("completion=256tok") }
    }

    @Test
    fun `sync 호출 시 metadata usage가 null이면 TokenEstimator 추정값을 로그에 기록한다`() {
        val request = mockRequest()
        val response = mockResponseWithoutUsage()
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(response)

        loggingAdvisor.adviseCall(request, chain)

        val logs = appender.list.map { it.formattedMessage }
        assertThat(logs).anyMatch { it.contains("tok") && it.contains("ms") }
    }

    @Test
    fun `sync 호출 시 경과 시간이 0 이상으로 로그에 기록된다`() {
        val request = mockRequest()
        val response = mockResponseWithUsage()
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(response)

        loggingAdvisor.adviseCall(request, chain)

        val logs = appender.list.map { it.formattedMessage }
        assertThat(logs).anyMatch { it.matches(Regex(".*\\|\\s*\\d+ms.*")) }
    }

    @Test
    fun `streaming 호출 시 집계 완료 후 로그를 1회 기록한다`() {
        val request = mockRequest()
        val response = mockResponseWithUsage()
        val streamChain = mock(StreamAdvisorChain::class.java)
        // 단일 청크 Flux 반환
        `when`(streamChain.nextStream(request)).thenReturn(Flux.just(response))

        val resultFlux = loggingAdvisor.adviseStream(request, streamChain)
        // Flux를 구독하여 완전히 소비해야 집계 및 로깅이 실행됨
        StepVerifier.create(resultFlux)
            .expectNextCount(1)
            .verifyComplete()

        // 로그가 1회 기록됐는지 확인
        val logs = appender.list.map { it.formattedMessage }
        assertThat(logs).hasSize(1)
        assertThat(logs[0]).contains("[LLM]")
    }

    @Test
    fun `getName은 LoggingAdvisor를 반환한다`() {
        assertThat(loggingAdvisor.getName()).isEqualTo("LoggingAdvisor")
    }
}
