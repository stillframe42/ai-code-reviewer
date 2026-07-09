package stillframe42.aicodereviewer.common

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import stillframe42.aicodereviewer.common.exception.AiResponseException
import stillframe42.aicodereviewer.common.exception.NotFoundException

// 전역 예외 처리 핸들러 — Problem Details 자동 핸들러보다 높은 우선순위로 등록
// Exception catch-all 이 이후 순위의 모든 advice 를 가리므로, feature 전용 advice
// (AgentExceptionHandler 등)가 먼저 평가되도록 HIGHEST_PRECEDENCE 보다 낮게 둔다
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RestControllerAdvice
class GlobalExceptionHandler : Logging {

    // @Valid 검증 실패 시 400 Bad Request 반환
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "유효하지 않은 값입니다") }
        return ResponseEntity.badRequest().body(errors)
    }

    // 요청 본문 파싱 실패(필드 누락, 타입 불일치 등) 시 400 Bad Request 반환
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to "요청 본문을 읽을 수 없습니다. 필드 누락 또는 타입 오류를 확인해주세요"))

    // 존재하지 않는 경로 요청 시 404 Not Found 반환
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to "요청한 경로를 찾을 수 없습니다: ${ex.resourcePath}"))

    // 도메인 단건 조회 결과 미존재 시 404 Not Found 반환
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (ex.message ?: "리소스를 찾을 수 없습니다")))

    // 잘못된 인자 예외 — 400 Bad Request 반환 (500 catch-all과 구분하기 위해 먼저 선언)
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "잘못된 요청입니다")))

    // AI 응답 수신 실패 — 의도된 사용자 대상 메시지이므로 message 를 그대로 노출한다 (docs/openapi.yml 500 예시와 계약)
    @ExceptionHandler(AiResponseException::class)
    fun handleAiResponseException(ex: AiResponseException): ResponseEntity<Map<String, String>> {
        logger.error("[GLOBAL] AI 응답 실패 — 500 반환: {}", ex.message)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (ex.message ?: "AI 응답 처리 중 오류가 발생했습니다")))
    }

    // 일반 예외 처리 — 500 Internal Server Error 반환
    // 응답은 고정 문구 — 미분류 예외의 message 는 내부 구현 정보라 노출하지 않고 로그로만 남긴다
    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<Map<String, String>> {
        logger.error("[GLOBAL] 미분류 예외 — 500 반환", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "서버 오류가 발생했습니다"))
    }
}
