package stillframe42.aicodereviewer.agent.application

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.agent.adapter.`in`.web.dto.AgentCallbackResult
import stillframe42.aicodereviewer.agent.domain.port.`in`.AgentCallbackUseCase
import stillframe42.aicodereviewer.common.Logging

// AgentCallbackUseCase 의 기본 구현체 — Kafka 도입 전 골격 단계라 INFO 로그만 남긴다
// 현재 라우팅 (AgentReviewService → AgentPoller) 은 이 핸들러를 호출하지 않는다
// Kafka 통합 시 dispatch/persist/notify 등 실 비즈니스 로직을 이 클래스에 추가 예정
@Component
class DefaultAgentCallbackHandler : AgentCallbackUseCase, Logging {

    override suspend fun handle(result: AgentCallbackResult) {
        logger.info(
            "agent callback received: id={}, status={}, issues={}, hasError={}",
            result.analysisId,
            result.status,
            result.issues.size,
            result.error != null,
        )
    }
}
