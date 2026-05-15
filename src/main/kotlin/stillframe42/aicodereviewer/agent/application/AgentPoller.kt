package stillframe42.aicodereviewer.agent.application

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisFailedException
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisTimeoutException
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisResult
import stillframe42.aicodereviewer.agent.domain.port.out.AgentAnalysisPort
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.RemoteAgentProperties
import kotlin.time.toKotlinDuration

@Component
class AgentPoller(
    private val port: AgentAnalysisPort,
    properties: RemoteAgentProperties,
) : Logging {

    private val maxAttempts = properties.poll.maxAttempts
    private val interval = properties.poll.interval.toKotlinDuration()
    private val timeout = properties.poll.timeout.toKotlinDuration()

    suspend fun pollUntilComplete(analysisId: String): AgentAnalysisResult {
        logger.info(
            "polling started: id={}, maxAttempts={}, interval={}, timeout={}",
            analysisId, maxAttempts, interval, timeout,
        )
        val startNanos = System.nanoTime()
        return try {
            withTimeout(timeout) {
                repeat(maxAttempts) { attempt ->
                    val result = port.getAnalysisResult(analysisId)
                    when (result.status) {
                        STATUS_DONE -> {
                            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                            logger.info(
                                "polling done: id={}, attempts={}, elapsed={}ms",
                                analysisId, attempt + 1, elapsedMs,
                            )
                            return@withTimeout result
                        }
                        STATUS_FAILED -> throw AgentAnalysisFailedException(analysisId, result.error)
                        else -> delay(interval)
                    }
                }
                throw AgentAnalysisTimeoutException(analysisId, "max attempts $maxAttempts reached")
            }
        } catch (e: TimeoutCancellationException) {
            throw AgentAnalysisTimeoutException(analysisId, "wall-clock timeout $timeout exceeded", e)
        }
    }

    companion object {
        private const val STATUS_DONE = "DONE"
        private const val STATUS_FAILED = "FAILED"
    }
}
