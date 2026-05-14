package stillframe42.aicodereviewer.rag.adapter.`in`.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.auth.InternalAuthValidator
import stillframe42.aicodereviewer.common.exception.NotFoundException
import stillframe42.aicodereviewer.rag.adapter.`in`.web.dto.RagContextResponse
import stillframe42.aicodereviewer.rag.domain.port.`in`.GetRagContextUseCase

// Python 에이전트가 AgentAnalysisRequest 의 context_ids 로 컨벤션 컨텍스트를 lazy fetch 하는 엔드포인트.
// 현재 sync HTTP flow 기준으로는 inline 전달 대비 net 네트워크 비용이 늘어난다 (POST 바디는 줄지만 GET 라운드트립 N 회 추가).
// 이 구조의 진짜 이점은 Kafka 전환 후에 살아남 — 메시지 크기 한계(default 1MB) + 컨슈머 그룹 fan-out 시 N 배 증폭 회피.
// X-Internal-Auth 헤더 토큰 검증 — AgentCallbackController 와 동일한 가드 패턴.
@RestController
@RequestMapping("/api/rag/context")
class RagContextController(
    private val useCase: GetRagContextUseCase,
    private val authValidator: InternalAuthValidator,
) : Logging {

    @GetMapping("/{contextId}")
    suspend fun get(
        @PathVariable contextId: String,
        @RequestHeader("X-Internal-Auth", required = false) authToken: String?,
    ): ResponseEntity<RagContextResponse> {
        if (!authValidator.isAuthorized(authToken)) {
            logger.warn("rag context unauthorized: contextId={}", contextId)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val context = useCase.get(contextId) ?: throw NotFoundException("contextId=$contextId not found")
        return ResponseEntity.ok(RagContextResponse(content = context.content))
    }
}
