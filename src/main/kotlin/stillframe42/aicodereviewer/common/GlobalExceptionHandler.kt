package stillframe42.aicodereviewer.common

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 전역 예외 처리 핸들러 — Problem Details 자동 핸들러보다 높은 우선순위로 등록
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class GlobalExceptionHandler {

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

    // 일반 예외 처리 — 500 Internal Server Error 반환
    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (ex.message ?: "서버 오류가 발생했습니다")))
}
