package stillframe42.aicodereviewer.common.advisor

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

class RetryAdvisorTest {

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    // Logback ListAppender로 RetryAdvisor 로그를 캡처
    @BeforeEach
    fun setUpLogger() {
        logger = LoggerFactory.getLogger(RetryAdvisor::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    @AfterEach
    fun tearDownLogger() {
        logger.detachAppender(appender)
    }

    private fun mockRequest() = mock(ChatClientRequest::class.java)
    private fun mockResponse() = mock(ChatClientResponse::class.java)

    @Test
    fun `5xx 오류 1회 실패 후 성공 시 최종 응답을 반환한다`() {
        // maxAttempts=2이므로 1초 sleep 발생 (IO 스레드 블로킹 허용 범위)
        val advisor = RetryAdvisor(maxAttempts = 2)
        val request = mockRequest()
        val expectedResponse = mockResponse()
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request))
            .thenThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .thenReturn(expectedResponse)

        val result = advisor.adviseCall(request, chain)

        assertThat(result).isSameAs(expectedResponse)
        verify(chain, times(2)).nextCall(request)
    }

    @Test
    fun `최대 재시도 초과 시 원래 예외를 그대로 전파한다`() {
        // maxAttempts=1이면 재시도 없이 즉시 throw — sleep 없음
        val advisor = RetryAdvisor(maxAttempts = 1)
        val request = mockRequest()
        val chain = mock(CallAdvisorChain::class.java)
        val exception = HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
        `when`(chain.nextCall(request)).thenThrow(exception)

        assertThatThrownBy { advisor.adviseCall(request, chain) }
            .isSameAs(exception)
        verify(chain, times(1)).nextCall(request)
    }

    @Test
    fun `429 Rate Limit 오류는 재시도한다`() {
        val advisor = RetryAdvisor(maxAttempts = 2)
        val request = mockRequest()
        val expectedResponse = mockResponse()
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request))
            .thenThrow(HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
            .thenReturn(expectedResponse)

        val result = advisor.adviseCall(request, chain)

        assertThat(result).isSameAs(expectedResponse)
        verify(chain, times(2)).nextCall(request)
    }

    @Test
    fun `4xx 비-429 오류는 즉시 throw하고 재시도하지 않는다`() {
        val advisor = RetryAdvisor(maxAttempts = 2)
        val request = mockRequest()
        val chain = mock(CallAdvisorChain::class.java)
        val exception = HttpClientErrorException(HttpStatus.BAD_REQUEST)
        `when`(chain.nextCall(request)).thenThrow(exception)

        assertThatThrownBy { advisor.adviseCall(request, chain) }
            .isSameAs(exception)
        // 재시도 없이 1회만 호출되었는지 확인
        verify(chain, times(1)).nextCall(request)
    }

    @Test
    fun `재시도 전 WARN 로그가 기록된다`() {
        val advisor = RetryAdvisor(maxAttempts = 2)
        val request = mockRequest()
        val expectedResponse = mockResponse()
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request))
            .thenThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .thenReturn(expectedResponse)

        advisor.adviseCall(request, chain)

        val logs = appender.list.map { it.formattedMessage }
        assertThat(logs).anyMatch { it.contains("[RETRY]") && it.contains("1/2") }
    }

    @Test
    fun `재시도 없이 즉시 throw 시 WARN 로그를 기록하지 않는다`() {
        // maxAttempts=1이면 재시도 시도 자체가 없으므로 WARN 로그도 없어야 함
        val advisor = RetryAdvisor(maxAttempts = 1)
        val request = mockRequest()
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request))
            .thenThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))

        runCatching { advisor.adviseCall(request, chain) }

        assertThat(appender.list).isEmpty()
    }

    @Test
    fun `getName은 RetryAdvisor를 반환한다`() {
        assertThat(RetryAdvisor().getName()).isEqualTo("RetryAdvisor")
    }
}
