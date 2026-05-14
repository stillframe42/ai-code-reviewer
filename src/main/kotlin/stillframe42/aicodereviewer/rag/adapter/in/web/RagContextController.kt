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

// X-Internal-Auth 헤더 토큰 검증 — AgentCallbackController 와 동일한 가드 패턴
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
