package stillframe42.aicodereviewer.common.langfuse

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import org.springframework.ai.chat.observation.ChatModelObservationContext
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.review.domain.model.ReviewContext

// Spring AI LLM 호출 관찰 이벤트를 수신해 Langfuse Trace/Generation으로 기록하는 핸들러
// Langfuse 전송 실패는 메인 플로우를 중단시키지 않는다
class LangfuseObservationHandler(
    private val langfuseClient: LangfuseClient,
) : ObservationHandler<ChatModelObservationContext>, Logging {

    // context 인스턴스를 키로 traceId/generationId를 보관 — onStart~onStop 동안 동일 인스턴스 보장
    private data class TraceInfo(val traceId: String, val generationId: String, val startTime: String)
    private val traceInfoMap: MutableMap<ChatModelObservationContext, TraceInfo> =
        Collections.synchronizedMap(IdentityHashMap())

    override fun supportsContext(context: Observation.Context): Boolean =
        context is ChatModelObservationContext

    // LLM 호출 시작 시: Langfuse에 Trace + Generation 생성
    // Tool Calling 시나리오: 첫 번째 LLM 호출 후 tool 실행, 두 번째 LLM 호출이 이어진다.
    // traceId는 SpringAiReviewAdapter가 코루틴 컨텍스트로 사전 전달 — 없으면 신규 생성 (chat 플로우 대비 fallback)
    override fun onStart(context: ChatModelObservationContext) {
        try {
            // traceId: SpringAiReviewAdapter가 사전 생성해 코루틴 컨텍스트로 전달. 없으면 생성
            val traceId = LangfuseTraceContextHolder.get() ?: UUID.randomUUID().toString()
            val generationId = UUID.randomUUID().toString()
            val startTime = Instant.now().toString()

            // onStop에서 재사용하기 위해 IdentityHashMap에 저장
            traceInfoMap[context] = TraceInfo(traceId, generationId, startTime)
            // (LangfuseTraceContextHolder.set 호출 불필요 — 코루틴 컨텍스트가 이미 설정됨)

            val reviewContext = ReviewObservationContextHolder.local.get()
            val metadata = buildMetadata(reviewContext)

            // trace-create + generation-create를 한 번의 배치로 전송
            // 같은 traceId가 이미 있으면 trace-create는 Langfuse에서 멱등 처리됨
            langfuseClient.ingest(
                listOf(
                    buildTraceCreate(traceId, startTime, metadata),
                    buildGenerationCreate(traceId, generationId, startTime, context, metadata),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] onStart 전송 실패 (무시): {}", e.message)
        }
    }

    // LLM 응답 수신 후: Generation에 출력, 토큰, 종료 시간 업데이트
    // traceId는 Tool Calling 전체 흐름이 끝날 때까지 유지 — SpringAiReviewAdapter withContext 종료 시 소멸
    override fun onStop(context: ChatModelObservationContext) {
        try {
            val traceInfo = traceInfoMap.remove(context) ?: return
            val generationId = traceInfo.generationId
            val endTime = Instant.now().toString()

            val response = context.response
            val outputText = response?.result?.output?.text.orEmpty()
            val model = response?.metadata?.model
            val usage = response?.metadata?.usage
            val promptTokens = usage?.promptTokens ?: 0
            val completionTokens = usage?.completionTokens ?: 0

            langfuseClient.ingest(
                listOf(
                    buildGenerationUpdate(generationId, endTime, outputText, promptTokens, completionTokens, model),
                ),
            )
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] onStop 전송 실패 (무시): {}", e.message)
        }
    }

    // LLM 호출 에러 시: Generation을 에러 상태로 업데이트
    // traceId 정리는 SpringAiReviewAdapter withContext 블록의 finally에서 처리
    override fun onError(context: ChatModelObservationContext) {
        try {
            val traceInfo = traceInfoMap.remove(context) ?: return
            val endTime = Instant.now().toString()

            langfuseClient.ingest(listOf(
                mapOf(
                    "type" to "generation-update",
                    "id" to UUID.randomUUID().toString(),
                    "timestamp" to endTime,
                    "body" to mapOf(
                        "id" to traceInfo.generationId,
                        "endTime" to endTime,
                        "level" to "ERROR",
                    ),
                )
            ))
        } catch (e: Exception) {
            logger.warn("[LANGFUSE] onError 전송 실패 (무시): {}", e.message)
        }
    }

    // ReviewContext → Langfuse metadata Map 변환
    private fun buildMetadata(reviewContext: ReviewContext?): Map<String, String> =
        if (reviewContext != null) mapOf(
            "review.request.id" to reviewContext.reviewRequestId.toString(),
            "review.pr.number" to reviewContext.prNumber.toString(),
            "review.repo" to reviewContext.repoFullName,
        ) else emptyMap()

    private fun buildTraceCreate(traceId: String, timestamp: String, metadata: Map<String, String>): Map<String, Any> =
        mapOf(
            "type" to "trace-create",
            "id" to UUID.randomUUID().toString(),
            "timestamp" to timestamp,
            "body" to mapOf(
                "id" to traceId,
                "name" to "ai-code-review",
                "metadata" to metadata,
            ),
        )

    private fun buildGenerationCreate(
        traceId: String,
        generationId: String,
        startTime: String,
        context: ChatModelObservationContext,
        metadata: Map<String, String>,
    ): Map<String, Any> {
        // onStart() 시점에는 아직 응답이 없으므로 모델명을 알 수 없음 — onStop()에서 generation-update로 덮어씀
        val promptText = context.request?.instructions
            ?.joinToString("\n") { "${it.messageType}: ${it.text.orEmpty()}" }
            .orEmpty()

        return mapOf(
            "type" to "generation-create",
            "id" to UUID.randomUUID().toString(),
            "timestamp" to startTime,
            "body" to mapOf(
                "id" to generationId,
                "traceId" to traceId,
                "name" to "chat-model",
                "startTime" to startTime,
                "input" to promptText,
                "metadata" to metadata,
            ),
        )
    }

    private fun buildGenerationUpdate(
        generationId: String,
        endTime: String,
        outputText: String,
        promptTokens: Int,
        completionTokens: Int,
        model: String?,
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "id" to generationId,
            "endTime" to endTime,
            "output" to outputText,
            "usage" to mapOf(
                "input" to promptTokens,
                "output" to completionTokens,
                "total" to (promptTokens + completionTokens),
                "unit" to "TOKENS",
            ),
        )
        // onStart() 시점에는 응답이 없어 "unknown"으로 기록되므로, onStop()에서 실제 모델명으로 덮어씀
        if (model != null) body["model"] = model

        return mapOf(
            "type" to "generation-update",
            "id" to UUID.randomUUID().toString(),
            "timestamp" to endTime,
            "body" to body,
        )
    }
}
