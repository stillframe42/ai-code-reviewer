package stillframe42.aicodereviewer.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import stillframe42.aicodereviewer.common.exception.AiResponseException

// 응답 본문 정책 단위 테스트 — HTTP 흐름(advice 등록·경로 매핑)은 GlobalExceptionHandlerTest(IT)가 검증
class GlobalExceptionHandlerUnitTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `미분류 예외는 500과 고정 문구를 반환하고 예외 message를 노출하지 않는다`() {
        val response = handler.handleException(IllegalStateException("DB 커넥션 풀 고갈: jdbc:postgresql://internal-host"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body).isEqualTo(mapOf("error" to "서버 오류가 발생했습니다"))
    }

    @Test
    fun `AiResponseException은 500과 예외 message를 그대로 반환한다`() {
        val response = handler.handleAiResponseException(AiResponseException("AI로부터 응답을 받지 못했습니다"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body).isEqualTo(mapOf("error" to "AI로부터 응답을 받지 못했습니다"))
    }

    @Test
    fun `IllegalArgumentException은 400과 예외 message를 반환한다`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException("provider 값이 유효하지 않습니다"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body).isEqualTo(mapOf("error" to "provider 값이 유효하지 않습니다"))
    }
}
