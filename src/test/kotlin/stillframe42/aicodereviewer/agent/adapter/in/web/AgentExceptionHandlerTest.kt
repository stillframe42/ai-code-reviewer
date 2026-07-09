package stillframe42.aicodereviewer.agent.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisFailedException
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisTimeoutException
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException

class AgentExceptionHandlerTest {

    private val handler = AgentExceptionHandler()

    @Test
    fun `AgentUnavailableException은 503으로 매핑된다`() {
        val response = handler.handleAgentException(AgentUnavailableException("connection refused"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body).isEqualTo(mapOf("error" to "에이전트 서비스에 연결할 수 없습니다"))
    }

    @Test
    fun `AgentAnalysisTimeoutException은 504로 매핑된다`() {
        val response = handler.handleAgentException(
            AgentAnalysisTimeoutException(analysisId = "analysis-1", reason = "poll exhausted")
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        assertThat(response.body).isEqualTo(mapOf("error" to "에이전트 분석이 시간 내에 완료되지 않았습니다"))
    }

    @Test
    fun `AgentAnalysisFailedException은 502로 매핑된다`() {
        val response = handler.handleAgentException(
            AgentAnalysisFailedException(analysisId = "analysis-1", agentError = "internal")
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        assertThat(response.body).isEqualTo(mapOf("error" to "에이전트 분석이 실패했습니다"))
    }

    @Test
    fun `응답 본문에 예외 message의 내부 식별자가 노출되지 않는다`() {
        val response = handler.handleAgentException(
            AgentAnalysisFailedException(analysisId = "analysis-secret-id", agentError = "stack trace...")
        )

        assertThat(response.body?.get("error")).doesNotContain("analysis-secret-id")
    }
}
