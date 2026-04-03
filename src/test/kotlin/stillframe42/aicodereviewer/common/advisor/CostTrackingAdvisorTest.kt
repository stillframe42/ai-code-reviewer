package stillframe42.aicodereviewer.common.advisor

import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.adapter.out.persistence.LlmCostLogRepository

// CostTrackingAdvisor 통합 테스트 — 실제 DB(Testcontainers)에 비용이 저장되는지 검증
class CostTrackingAdvisorTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var costTrackingAdvisor: CostTrackingAdvisor

    @Autowired
    private lateinit var costLogRepository: LlmCostLogRepository

    // 테스트 간 DB 오염 방지
    @BeforeEach
    fun cleanCostLogs() {
        costLogRepository.deleteAll()
    }

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

    @Test
    fun `비용을 계산하여 DB에 저장한다`() {
        // claude-haiku-4-5-20251001: input=0.000800/1k, output=0.004000/1k
        // 예상 비용: (100/1000 * 0.000800) + (50/1000 * 0.004000) = 0.000080 + 0.000200 = 0.000280
        val request = mock(ChatClientRequest::class.java)
        val response = mockResponseWithUsage("claude-haiku-4-5-20251001", 100, 50)
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(response)

        costTrackingAdvisor.adviseCall(request, chain)

        // 비동기 저장 완료 대기
        await.atMost(2, TimeUnit.SECONDS).until { costLogRepository.count() == 1L }

        val log = costLogRepository.findAll().first()
        assertThat(log.modelName).isEqualTo("claude-haiku-4-5-20251001")
        assertThat(log.promptTokens).isEqualTo(100)
        assertThat(log.completionTokens).isEqualTo(50)
        assertThat(log.estimatedCostUsd).isEqualByComparingTo(BigDecimal("0.000280"))
        assertThat(log.reviewRequestId).isNull()
    }

    @Test
    fun `단가 설정에 없는 모델은 비용 0으로 저장한다`() {
        val request = mock(ChatClientRequest::class.java)
        val response = mockResponseWithUsage("unknown-model-xyz", 100, 50)
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(response)

        costTrackingAdvisor.adviseCall(request, chain)

        await.atMost(2, TimeUnit.SECONDS).until { costLogRepository.count() == 1L }

        val log = costLogRepository.findAll().first()
        assertThat(log.estimatedCostUsd).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(log.modelName).isEqualTo("unknown-model-xyz")
    }

    @Test
    fun `chatResponse가 null이면 저장하지 않고 원래 응답을 반환한다`() {
        // usage가 null인 응답 → saveCostLog가 조용히 리턴 → adviseCall은 정상 반환
        val request = mock(ChatClientRequest::class.java)
        val response = mock(ChatClientResponse::class.java)
        `when`(response.chatResponse()).thenReturn(null)
        val chain = mock(CallAdvisorChain::class.java)
        `when`(chain.nextCall(request)).thenReturn(response)

        val result = costTrackingAdvisor.adviseCall(request, chain)

        // 예외 없이 원래 응답을 그대로 반환해야 함
        assertThat(result).isSameAs(response)
    }
}
