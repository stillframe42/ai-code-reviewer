package stillframe42.aicodereviewer.review.adapter.out.ai

import java.time.Instant
import java.util.UUID
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.langfuse.LangfuseClient
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder
import stillframe42.aicodereviewer.review.domain.port.out.ToolObservationPort

// Tool 실행을 Langfuse Child Span으로 기록하는 어댑터
// traceId는 LangfuseTraceContextHolder에서 읽는다 — LangfuseObservationHandler.onStart()가 설정한 값
class LangfuseToolSpanAdapter(
    private val langfuseClient: LangfuseClient,
) : ToolObservationPort, Logging {

    override fun startSpan(toolName: String, input: Map<String, Any>): String {
        val traceId = LangfuseTraceContextHolder.get() ?: return ""
        val spanId = UUID.randomUUID().toString()
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "span-create",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to Instant.now().toString(),
                        "body" to mapOf(
                            "id" to spanId,
                            "traceId" to traceId,
                            "name" to toolName,
                            "startTime" to Instant.now().toString(),
                            "input" to input,
                        ),
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] span-create 전송 실패 (무시): {}", e.message)
        }
        return spanId
    }

    override fun endSpan(spanId: String, output: String) {
        if (spanId.isEmpty()) return
        val traceId = LangfuseTraceContextHolder.get() ?: return
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "span-update",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to Instant.now().toString(),
                        "body" to mapOf(
                            "id" to spanId,
                            "traceId" to traceId,
                            "endTime" to Instant.now().toString(),
                            "output" to output,
                        ),
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] span-update 전송 실패 (무시): {}", e.message)
        }
    }

    override fun endSpanWithError(spanId: String, error: String) {
        if (spanId.isEmpty()) return
        val traceId = LangfuseTraceContextHolder.get() ?: return
        try {
            langfuseClient.ingest(
                listOf(
                    mapOf(
                        "type" to "span-update",
                        "id" to UUID.randomUUID().toString(),
                        "timestamp" to Instant.now().toString(),
                        "body" to mapOf(
                            "id" to spanId,
                            "traceId" to traceId,
                            "endTime" to Instant.now().toString(),
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
