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
