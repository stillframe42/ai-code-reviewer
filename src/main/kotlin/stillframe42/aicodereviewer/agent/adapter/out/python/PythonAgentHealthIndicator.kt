package stillframe42.aicodereviewer.agent.adapter.out.python

import kotlinx.coroutines.reactor.mono
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort

// suspend fun checkHealth() 을 ReactiveHealthIndicator 로 노출 — kotlinx-coroutines-reactor 의
// mono 빌더로 코루틴 호출을 그대로 사용해 runBlocking 사용을 회피한다.
@Component
class PythonAgentHealthIndicator(
    private val port: AgentAnalysisPort,
) : ReactiveHealthIndicator {

    override fun health(): Mono<Health> = mono {
        // port.checkHealth() 의 현재 구현은 예외를 흡수하지만, 인터페이스 계약상 suspend 호출이 던질 수 있는
        // 가능성을 안전망으로 처리한다 — indicator 자체가 예외로 깨지지 않도록 보장.
        runCatching { port.checkHealth() }
            .fold(
                onSuccess = { ok -> healthFromBoolean(ok) },
                onFailure = { ex -> Health.down(ex).build() },
            )
    }

    private fun healthFromBoolean(ok: Boolean): Health =
        if (ok) Health.up().build() else Health.down().build()
}
