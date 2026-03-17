package stillframe42.aicodereviewer.chat.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

// ChatRequest DTO 단위 테스트 — Spring 컨텍스트 없이 Validator 직접 사용
class ChatRequestTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `정상 메시지는 검증 통과`() {
        val request = ChatRequest(message = "Spring AI란 무엇인가요?")
        val violations = validator.validate(request)
        assertThat(violations).isEmpty()
    }

    @Test
    fun `빈 문자열은 검증 실패`() {
        val request = ChatRequest(message = "")
        val violations = validator.validate(request)
        assertThat(violations).hasSize(1)
        assertThat(violations.first().message).isEqualTo("메시지를 입력해주세요")
    }

    @Test
    fun `공백만 있는 문자열은 검증 실패`() {
        val request = ChatRequest(message = "   ")
        val violations = validator.validate(request)
        assertThat(violations).hasSize(1)
        assertThat(violations.first().message).isEqualTo("메시지를 입력해주세요")
    }
}
