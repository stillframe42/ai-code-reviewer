package stillframe42.aicodereviewer.common.langfuse

import io.micrometer.observation.Observation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.ai.chat.observation.ChatModelObservationContext

// LangfuseObservationHandler 단위 테스트 — Spring 컨텍스트 없이 직접 호출
class LangfuseObservationHandlerTest {

    private lateinit var langfuseClient: LangfuseClient
    private lateinit var handler: LangfuseObservationHandler

    @BeforeEach
    fun setUp() {
        langfuseClient = mock(LangfuseClient::class.java)
        handler = LangfuseObservationHandler(langfuseClient)
        ObservationSessionContextHolder.local.remove()
        LangfuseTraceContextHolder.clear()
    }

    @AfterEach
    fun tearDown() {
        LangfuseTraceContextHolder.clear()
        ObservationSessionContextHolder.local.remove()
    }

    @Test
    fun `supportsContext는 ChatModelObservationContext만 true를 반환한다`() {
        val chatContext = mock(ChatModelObservationContext::class.java)
        val otherContext = mock(Observation.Context::class.java)

        assertThat(handler.supportsContext(chatContext)).isTrue()
        assertThat(handler.supportsContext(otherContext)).isFalse()
    }

    @Test
    fun `onStart에서 langfuseClient ingest를 호출한다`() {
        val context = mock(ChatModelObservationContext::class.java)

        handler.onStart(context)

        verify(langfuseClient).ingest(anyList())
    }

    @Test
    fun `onStop에서 langfuseClient ingest를 호출한다`() {
        val context = mock(ChatModelObservationContext::class.java)

        handler.onStart(context)
        handler.onStop(context)

        // ingest는 onStart(1번) + onStop(1번) = 2번 호출
        verify(langfuseClient, times(2)).ingest(anyList())
    }

    @Test
    fun `langfuseClient 실패 시 예외가 전파되지 않는다`() {
        val context = mock(ChatModelObservationContext::class.java)
        `when`(langfuseClient.ingest(anyList())).thenThrow(RuntimeException("connection refused"))

        // 예외 없이 정상 완료해야 함
        handler.onStart(context)
    }

    @Test
    fun `onStop에서 langfuseClient 실패 시 예외가 전파되지 않는다`() {
        val context = mock(ChatModelObservationContext::class.java)
        handler.onStart(context)
        `when`(langfuseClient.ingest(anyList())).thenThrow(RuntimeException("connection refused"))

        // 예외 없이 정상 완료해야 함
        handler.onStop(context)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `세션 컨텍스트가 있으면 trace-create 이벤트 metadata에 포함된다`() {
        ObservationSessionContextHolder.local.set(
            ObservationSessionContext(
                sessionId = "42",
                metadata = mapOf(
                    "review.request.id" to "42",
                    "review.pr.number" to "7",
                    "review.repo" to "owner/repo",
                ),
            )
        )
        val context = mock(ChatModelObservationContext::class.java)

        // Kotlin에서 Mockito capture()가 null을 반환하는 문제를 우회해 doAnswer로 인수를 직접 캡쳐
        var capturedBatch: List<Map<String, Any>>? = null
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedBatch = invocation.getArgument<List<Map<String, Any>>>(0)
            null
        }.`when`(langfuseClient).ingest(anyList())

        handler.onStart(context)

        // 첫 번째 이벤트(trace-create)의 body.metadata 확인
        val traceEvent = capturedBatch!!.first { it["type"] == "trace-create" }
        val body = traceEvent["body"] as Map<*, *>
        val metadata = body["metadata"] as Map<*, *>
        assertThat(metadata["review.request.id"]).isEqualTo("42")
        assertThat(metadata["review.pr.number"]).isEqualTo("7")
        assertThat(metadata["review.repo"]).isEqualTo("owner/repo")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `세션 컨텍스트가 있으면 trace-create body 에 sessionId가 reviewRequestId 문자열로 부착된다`() {
        ObservationSessionContextHolder.local.set(
            ObservationSessionContext(
                sessionId = "42",
                metadata = mapOf(
                    "review.request.id" to "42",
                    "review.pr.number" to "7",
                    "review.repo" to "owner/repo",
                ),
            )
        )
        val context = mock(ChatModelObservationContext::class.java)

        var capturedBatch: List<Map<String, Any>>? = null
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedBatch = invocation.getArgument<List<Map<String, Any>>>(0)
            null
        }.`when`(langfuseClient).ingest(anyList())

        handler.onStart(context)

        val traceEvent = capturedBatch!!.first { it["type"] == "trace-create" }
        val body = traceEvent["body"] as Map<*, *>
        assertThat(body["sessionId"]).isEqualTo("42")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `세션 컨텍스트가 없으면 trace-create body 에 sessionId 키가 없다`() {
        val context = mock(ChatModelObservationContext::class.java)

        var capturedBatch: List<Map<String, Any>>? = null
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedBatch = invocation.getArgument<List<Map<String, Any>>>(0)
            null
        }.`when`(langfuseClient).ingest(anyList())

        handler.onStart(context)

        val traceEvent = capturedBatch!!.first { it["type"] == "trace-create" }
        val body = traceEvent["body"] as Map<*, *>
        assertThat(body.containsKey("sessionId")).isFalse()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `LangfuseTraceContextHolder에 traceId가 있으면 onStart는 기존 traceId를 사용한다`() {
        val existingTraceId = "existing-trace-id"
        LangfuseTraceContextHolder.set(existingTraceId)
        val context = mock(ChatModelObservationContext::class.java)

        // Langfuse에 전송된 이벤트 배치를 캡쳐
        var capturedBatch: List<Map<String, Any>>? = null
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedBatch = invocation.getArgument<List<Map<String, Any>>>(0)
            null
        }.`when`(langfuseClient).ingest(anyList())

        handler.onStart(context)

        // trace-create 이벤트의 body.id가 기존 traceId여야 한다
        val traceEvent = capturedBatch!!.first { it["type"] == "trace-create" }
        val body = traceEvent["body"] as Map<*, *>
        assertThat(body["id"]).isEqualTo(existingTraceId)

        // generation-create 이벤트의 body.traceId도 기존 traceId여야 한다
        val generationEvent = capturedBatch!!.first { it["type"] == "generation-create" }
        val generationBody = generationEvent["body"] as Map<*, *>
        assertThat(generationBody["traceId"]).isEqualTo(existingTraceId)
    }
}
