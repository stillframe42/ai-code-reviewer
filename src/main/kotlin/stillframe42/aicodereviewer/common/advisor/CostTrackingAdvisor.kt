package stillframe42.aicodereviewer.common.advisor

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.core.Ordered
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.LlmCostProperties
import stillframe42.aicodereviewer.review.adapter.out.persistence.LlmCostLogRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.LlmCostLogEntity

// AI 호출 비용을 계산하여 DB에 비동기로 기록하는 어드바이저
// StreamAdvisor는 구현하지 않는다 — usage 메타데이터는 전체 응답 완료 후에만 확인 가능하다
class CostTrackingAdvisor(
    private val costProperties: LlmCostProperties,
    private val costLogRepository: LlmCostLogRepository,
    // CoroutineScope 주입 — 테스트에서 교체 가능하도록 설계
    // SupervisorJob: 개별 저장 실패가 scope를 취소하지 않도록 격리
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val order: Int = Ordered.LOWEST_PRECEDENCE,
) : CallAdvisor, Logging {

    override fun getName(): String = "CostTrackingAdvisor"

    // AI 호출 직후(가장 안쪽)에서 실행 — 실제 usage 메타데이터를 확보하기 위해 LOWEST_PRECEDENCE
    override fun getOrder(): Int = order

    override fun adviseCall(request: ChatClientRequest, chain: CallAdvisorChain): ChatClientResponse {
        val response = chain.nextCall(request)
        // 메인 플로우를 블로킹하지 않고 백그라운드에서 비용 저장
        scope.launch {
            try {
                saveCostLog(response)
            } catch (e: Exception) {
                logger.warn("[COST] 비용 로그 저장 실패 (무시)", e)
            }
        }
        return response
    }

    // 응답 메타데이터에서 토큰 수를 추출해 비용을 계산하고 DB에 저장
    private fun saveCostLog(response: ChatClientResponse) {
        val chatResponse = response.chatResponse() ?: return
        val model = chatResponse.metadata?.model ?: return
        val usage = chatResponse.metadata?.usage ?: return

        val promptTokens = usage.promptTokens ?: 0
        val completionTokens = usage.completionTokens ?: 0
        val totalTokens = promptTokens + completionTokens
        val cost = calculateCost(model, promptTokens, completionTokens)

        logger.info(
            "[COST] model={} | tokens={} | est=\${}",
            model, totalTokens, cost,
        )

        costLogRepository.save(
            LlmCostLogEntity(
                modelName = model,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                estimatedCostUsd = cost,
            )
        )
    }

    // 모델별 단가를 적용해 예상 비용 계산 (USD)
    // 설정에 없는 모델은 0으로 처리 — WARN 로그로 운영자에게 알림
    private fun calculateCost(model: String, promptTokens: Int, completionTokens: Int): BigDecimal {
        val modelCost = costProperties.models[model]
        if (modelCost == null) {
            logger.warn("[COST] 모델 단가 설정 없음 — 비용 0으로 저장: model={}", model)
            return BigDecimal.ZERO
        }
        val inputCost = promptTokens.toBigDecimal()
            .multiply(modelCost.inputPer1k)
            .divide(BigDecimal(1000), 6, RoundingMode.HALF_UP)
        val outputCost = completionTokens.toBigDecimal()
            .multiply(modelCost.outputPer1k)
            .divide(BigDecimal(1000), 6, RoundingMode.HALF_UP)
        return (inputCost + outputCost).setScale(6, RoundingMode.HALF_UP)
    }
}
