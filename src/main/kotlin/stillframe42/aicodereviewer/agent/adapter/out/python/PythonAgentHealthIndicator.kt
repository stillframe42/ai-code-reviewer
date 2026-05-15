package stillframe42.aicodereviewer.agent.adapter.out.python

import kotlinx.coroutines.reactor.mono
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort

// Python agent /health 를 actuator 에 노출 — agent 는 optional dependency 이므로
// 별도 group endpoint /actuator/health/python-agent 로도 polling 가능하도록 구성한다.
@Component
class PythonAgentHealthIndicator(
    private val port: AgentAnalysisPort,
) : ReactiveHealthIndicator {

    override fun health(): Mono<Health> = mono {
        runCatching { port.checkHealth() }
            .fold(
                onSuccess = { ok -> healthFromBoolean(ok) },
                onFailure = { ex -> Health.down(ex).build() },
            )
    }

    private fun healthFromBoolean(ok: Boolean): Health =
        if (ok) Health.up().build() else Health.down().build()
}
