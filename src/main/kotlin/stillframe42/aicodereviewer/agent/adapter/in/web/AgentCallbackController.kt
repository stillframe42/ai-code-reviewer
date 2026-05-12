package stillframe42.aicodereviewer.agent.adapter.`in`.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.agent.adapter.`in`.web.dto.AgentCallbackResult
import stillframe42.aicodereviewer.agent.domain.port.`in`.AgentCallbackUseCase
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.PythonAgentProperties

// Python 에이전트 콜백 endpoint — Kafka 전환 전 임시 골격
// X-Internal-Auth 헤더 토큰 검증으로 외부 노출 차단 (Spring Security 부재 환경의 최소 가드)
@RestController
@RequestMapping("/internal/agent")
class AgentCallbackController(
    private val useCase: AgentCallbackUseCase,
    private val properties: PythonAgentProperties,
) : Logging {

    @PostMapping("/callback")
    suspend fun receiveAgentCallback(
        @RequestHeader("X-Internal-Auth", required = false) authToken: String?,
        @RequestBody result: AgentCallbackResult,
    ): ResponseEntity<Unit> {
        if (!isAuthorized(authToken)) {
            logger.warn("agent callback unauthorized: id={}", result.analysisId)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        useCase.handle(result)
        return ResponseEntity.accepted().build()
    }

    // 빈 토큰 설정(미설정 또는 빈 문자열)이면 모든 요청 거부 — config 누락 시 endpoint 사실상 비활성
    private fun isAuthorized(authToken: String?): Boolean {
        val expected = properties.callback.internalAuthToken
        return expected.isNotEmpty() && authToken == expected
    }
}
