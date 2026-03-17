package stillframe42.aicodereviewer.chat.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import stillframe42.aicodereviewer.chat.AiProvider

// ChatRequest DTO 단위 테스트 — Spring 컨텍스트 없이 Validator/ObjectMapper 직접 사용
class ChatRequestTest {

    private lateinit var validator: Validator
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
        objectMapper = ObjectMapper().registerKotlinModule()
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

    @Test
    fun `provider 생략 시 null로 파싱되어 컨트롤러에서 ANTHROPIC 기본값 처리됨`() {
        // Jackson 3(tools.jackson)은 Kotlin 기본값 파라미터를 지원하지 않으므로 nullable로 처리
        val request: ChatRequest = objectMapper.readValue("""{"message": "테스트"}""")
        assertThat(request.provider).isNull()
    }

    @Test
    fun `provider 필드에 OPENAI 전달 시 정상 파싱`() {
        val request: ChatRequest = objectMapper.readValue("""{"message": "테스트", "provider": "OPENAI"}""")
        assertThat(request.provider).isEqualTo(AiProvider.OPENAI)
    }
}
