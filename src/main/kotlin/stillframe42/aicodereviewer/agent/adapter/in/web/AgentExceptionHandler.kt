package stillframe42.aicodereviewer.agent.adapter.`in`.web

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisFailedException
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisTimeoutException
import stillframe42.aicodereviewer.agent.domain.exception.AgentException
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.common.Logging

// Agent 도메인 예외 → HTTP 상태 매핑 어드바이스
// GlobalExceptionHandler 의 Exception catch-all 이 모든 advice 를 가리므로,
// feature advice 가 먼저 평가되도록 catch-all 보다 높은 우선순위로 등록한다
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class AgentExceptionHandler : Logging {

    // 응답 메시지는 고정 문구 — 예외 message 의 내부 식별자(analysisId 등) 노출 방지
    @ExceptionHandler(AgentException::class)
    fun handleAgentException(ex: AgentException): ResponseEntity<Map<String, String>> {
        logger.warn("[AGENT] 도메인 예외 → HTTP 매핑: {}", ex.message)
        return when (ex) {
            is AgentUnavailableException ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(mapOf("error" to "에이전트 서비스에 연결할 수 없습니다"))
            is AgentAnalysisTimeoutException ->
                ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(mapOf("error" to "에이전트 분석이 시간 내에 완료되지 않았습니다"))
            is AgentAnalysisFailedException ->
                ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(mapOf("error" to "에이전트 분석이 실패했습니다"))
        }
    }
}
