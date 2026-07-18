package stillframe42.aicodereviewer.common.advisor

import java.math.BigDecimal
import java.util.concurrent.Executor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.context.ApplicationEventPublisher
import stillframe42.aicodereviewer.common.metrics.event.LlmCallCompletedEvent
import stillframe42.aicodereviewer.common.port.CostLogEntry
import stillframe42.aicodereviewer.common.port.CostLogPort
import stillframe42.aicodereviewer.config.LlmCostProperties

// 평문 단위 테스트 — 비용 계산·저장 로직을 fake 포트로 검증
class CostTrackingAdvisorTest {

    private val savedEntries = mutableListOf<CostLogEntry>()
    private val publishedEvents = mutableListOf<Any>()

    private val advisor = CostTrackingAdvisor(
        costProperties = haikuCostProperties(),
        costLogPort = object : CostLogPort {
            override fun save(entry: CostLogEntry) {
                savedEntries.add(entry)
            }
        },
        eventPublisher = ApplicationEventPublisher { publishedEvents.add(it) },
        // 호출 스레드에서 즉시 실행하는 동기 Executor — 비동기 대기(awaitility) 없이 저장 결과 검증
        executor = Executor { it.run() },
    )

    // 알려진 usage(promptTokens, completionTokens)가 담긴 ChatClientResponse mock 생성
    private fun mockResponseWithUsage(
        model: String,
        promptTokens: Int,
        completionTokens: Int,
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

    private fun mockChainReturning(request: ChatClientRequest, response: ChatClientResponse): CallAdvisorChain {
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(response)
        return chain
    }

    @Test
    fun `비용을 계산하여 포트에 저장하고 완료 이벤트를 발행한다`() {
        // claude-haiku-4-5-20251001: input=0.000800/1k, output=0.004000/1k
        // 예상 비용: (100/1000 * 0.000800) + (50/1000 * 0.004000) = 0.000080 + 0.000200 = 0.000280
        val request = mock(ChatClientRequest::class.java)
        val response = mockResponseWithUsage("claude-haiku-4-5-20251001", 100, 50)

        advisor.adviseCall(request, mockChainReturning(request, response))

        val entry = savedEntries.single()
        assertThat(entry.modelName).isEqualTo("claude-haiku-4-5-20251001")
        assertThat(entry.promptTokens).isEqualTo(100)
        assertThat(entry.completionTokens).isEqualTo(50)
        assertThat(entry.estimatedCostUsd).isEqualByComparingTo(BigDecimal("0.000280"))

        val event = publishedEvents.filterIsInstance<LlmCallCompletedEvent>().single()
        assertThat(event.costUsd).isEqualByComparingTo(BigDecimal("0.000280"))
    }

    @Test
    fun `단가 설정에 없는 모델은 비용 0으로 저장한다`() {
        val request = mock(ChatClientRequest::class.java)
        val response = mockResponseWithUsage("unknown-model-xyz", 100, 50)

        advisor.adviseCall(request, mockChainReturning(request, response))

        val entry = savedEntries.single()
        assertThat(entry.estimatedCostUsd).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(entry.modelName).isEqualTo("unknown-model-xyz")
    }

    @Test
    fun `chatResponse가 null이면 저장하지 않고 원래 응답을 반환한다`() {
        val request = mock(ChatClientRequest::class.java)
        val response = mock(ChatClientResponse::class.java)
        `when`(response.chatResponse()).thenReturn(null)

        val result = advisor.adviseCall(request, mockChainReturning(request, response))

        assertThat(result).isSameAs(response)
        assertThat(savedEntries).isEmpty()
        assertThat(publishedEvents).isEmpty()
    }
}

private fun haikuCostProperties() = LlmCostProperties(
    models = mapOf(
        "claude-haiku-4-5-20251001" to LlmCostProperties.ModelCost(
            inputPer1k = BigDecimal("0.000800"),
            outputPer1k = BigDecimal("0.004000"),
        ),
    ),
)
