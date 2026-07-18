package stillframe42.aicodereviewer.review.adapter.out.ai

import java.time.Instant
import java.util.UUID
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.langfuse.LangfuseClient
import stillframe42.aicodereviewer.common.langfuse.LangfuseSpanContextHolder
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder
import stillframe42.aicodereviewer.common.observability.SpanHandle
import stillframe42.aicodereviewer.review.domain.port.out.ToolObservationPort

// Tool 실행을 Langfuse Child Span으로 기록하는 어댑터
// traceId는 LangfuseTraceContextHolder에서 읽는다 — LangfuseObservationHandler.onStart()가 설정한 값
class LangfuseToolSpanAdapter(
    private val langfuseClient: LangfuseClient,
) : ToolObservationPort, Logging {

    override fun beginTrace() {
        LangfuseTraceContextHolder.set(UUID.randomUUID().toString())
    }

    override fun clearTrace() {
        LangfuseTraceContextHolder.clear()
        LangfuseSpanContextHolder.clear()
    }

    override fun currentSpanId(): String? = LangfuseSpanContextHolder.get()

    override fun activateSpan(spanId: String?) {
        if (spanId == null) LangfuseSpanContextHolder.clear() else LangfuseSpanContextHolder.set(spanId)
    }

    // ObservabilityPort — SpanHandle 반환
    override fun startSpan(name: String, input: Map<String, Any>, metadata: Map<String, Any>): SpanHandle {
        val traceId = LangfuseTraceContextHolder.get() ?: return SpanHandle(spanId = "", traceId = "")
        val parentSpanId = LangfuseSpanContextHolder.get()
        val spanId = UUID.randomUUID().toString()
        // timestamp와 startTime에 동일한 시각 값을 사용하기 위해 단 한 번만 캡처
        val now = Instant.now().toString()
        val body = mutableMapOf<String, Any>(
            "id" to spanId,
            "traceId" to traceId,
            "name" to name,
            "startTime" to now,
            "input" to input,
        )
        if (metadata.isNotEmpty()) body["metadata"] = metadata
        parentSpanId?.let { body["parentObservationId"] = it }
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "span-create",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to now,
                        "body" to body,
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] span-create 전송 실패 (무시): {}", e.message)
            // span-create 실패 시 빈 SpanHandle 반환 → endSpan에서 orphan update 방지
            return SpanHandle(spanId = "", traceId = "")
        }
        return SpanHandle(spanId = spanId, traceId = traceId)
    }

    // ObservabilityPort — SpanHandle + Map<String, Any> output
    override fun endSpan(handle: SpanHandle, output: Map<String, Any>, metadata: Map<String, Any>) {
        if (handle.spanId.isEmpty()) return
        // timestamp와 endTime에 동일한 시각 값을 사용하기 위해 단 한 번만 캡처
        val endTime = Instant.now().toString()
        val body = mutableMapOf<String, Any>(
            "id" to handle.spanId,
            "traceId" to handle.traceId,
            "endTime" to endTime,
            "output" to output,
        )
        if (metadata.isNotEmpty()) body["metadata"] = metadata
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "span-update",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to endTime,
                        "body" to body,
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] span-update 전송 실패 (무시): {}", e.message)
        }
    }

    // ObservabilityPort — SpanHandle
    override fun endSpanWithError(handle: SpanHandle, error: String) {
        if (handle.spanId.isEmpty()) return
        // timestamp와 endTime에 동일한 시각 값을 사용하기 위해 단 한 번만 캡처
        val endTime = Instant.now().toString()
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "span-update",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to endTime,
                        "body" to mapOf(
                            "id" to handle.spanId,
                            "traceId" to handle.traceId,
                            "endTime" to endTime,
                            "level" to "ERROR",
                            "statusMessage" to error,
                        ),
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] span-update(error) 전송 실패 (무시): {}", e.message)
        }
    }

    // ToolObservationPort (레거시) — ObservabilityPort 메서드에 위임
    override fun startSpan(toolName: String, input: Map<String, Any>): String =
        startSpan(name = toolName, input = input).spanId

    override fun endSpan(spanId: String, output: String) {
        if (spanId.isEmpty()) return
        val traceId = LangfuseTraceContextHolder.get() ?: return
        endSpan(SpanHandle(spanId, traceId), output = mapOf("result" to output))
    }

    override fun endSpanWithError(spanId: String, error: String) {
        if (spanId.isEmpty()) return
        val traceId = LangfuseTraceContextHolder.get() ?: return
        endSpanWithError(SpanHandle(spanId, traceId), error)
    }
}
