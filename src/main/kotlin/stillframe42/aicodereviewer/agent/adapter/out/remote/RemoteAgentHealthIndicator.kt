package stillframe42.aicodereviewer.agent.adapter.out.remote

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort

@Component
class RemoteAgentHealthIndicator(
    private val port: AgentAnalysisPort,
) : HealthIndicator {

    // port.checkHealth() 의 현재 구현은 예외를 흡수하지만, 인터페이스 계약상 던질 수 있는
    // 가능성을 안전망으로 처리한다 — indicator 자체가 예외로 깨지지 않도록 보장.
    override fun health(): Health =
        runCatching { port.checkHealth() }
            .fold(
                onSuccess = { ok -> if (ok) Health.up().build() else Health.down().build() },
                onFailure = { ex -> Health.down(ex).build() },
            )
}
