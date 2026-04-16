package stillframe42.aicodereviewer.review.adapter.out.ai

import java.time.Instant
import java.util.UUID
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.langfuse.LangfuseClient
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder
import stillframe42.aicodereviewer.common.observability.SpanHandle
import stillframe42.aicodereviewer.review.domain.port.out.ToolObservationPort

// Tool 실행을 Langfuse Child Span으로 기록하는 어댑터
// traceId는 LangfuseTraceContextHolder에서 읽는다 — LangfuseObservationHandler.onStart()가 설정한 값
class LangfuseToolSpanAdapter(
    private val langfuseClient: LangfuseClient,
) : ToolObservationPort, Logging {

    // ObservabilityPort — SpanHandle 반환
    // traceId가 없으면 자동으로 trace-create를 전송하고 holder에 설정한다
    // 이를 통해 review.root span이 trace를 시작하고 하위 span이 같은 trace에 연결된다
    override fun startSpan(name: String, input: Map<String, Any>, metadata: Map<String, Any>): SpanHandle {
        val traceId = LangfuseTraceContextHolder.get() ?: createTrace(name)
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

    // traceId가 존재하지 않을 때 새 trace를 생성하고 holder에 등록한다
    // review.root span 등 파이프라인 최상위에서 호출되어 전체 trace 컨텍스트를 시작한다
    private fun createTrace(name: String): String {
        val traceId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "trace-create",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to now,
                        "body" to mapOf(
                            "id" to traceId,
                            "name" to name,
                            "timestamp" to now,
                        ),
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] trace-create 전송 실패 (무시): {}", e.message)
        }
        LangfuseTraceContextHolder.set(traceId)
        return traceId
    }

    // ToolObservationPort (레거시) — String spanId 기반
    override fun startSpan(toolName: String, input: Map<String, Any>): String {
        val traceId = LangfuseTraceContextHolder.get() ?: return ""
        val spanId = UUID.randomUUID().toString()
        // timestamp와 startTime에 동일한 시각 값을 사용하기 위해 단 한 번만 캡처
        val now = Instant.now().toString()
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "span-create",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to now,
                        "body" to mapOf(
                            "id" to spanId,
                            "traceId" to traceId,
                            "name" to toolName,
                            "startTime" to now,
                            "input" to input,
                        ),
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] span-create 전송 실패 (무시): {}", e.message)
            // span-create 실패 시 빈 문자열 반환 → endSpan에서 orphan update 방지
            return ""
        }
        return spanId
    }

    // ToolObservationPort (레거시)
    override fun endSpan(spanId: String, output: String) {
        if (spanId.isEmpty()) return
        val traceId = LangfuseTraceContextHolder.get() ?: return
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
                            "id" to spanId,
                            "traceId" to traceId,
                            "endTime" to endTime,
                            "output" to output,
                        ),
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] span-update 전송 실패 (무시): {}", e.message)
        }
    }

    // ToolObservationPort (레거시)
    override fun endSpanWithError(spanId: String, error: String) {
        if (spanId.isEmpty()) return
        val traceId = LangfuseTraceContextHolder.get() ?: return
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
                            "id" to spanId,
                            "traceId" to traceId,
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
}
