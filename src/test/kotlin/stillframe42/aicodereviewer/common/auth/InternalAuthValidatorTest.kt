package stillframe42.aicodereviewer.common.auth

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import stillframe42.aicodereviewer.config.RemoteAgentProperties
import stillframe42.aicodereviewer.config.RemoteAgentProperties.CallbackProperties
import stillframe42.aicodereviewer.config.RemoteAgentProperties.PollProperties

class InternalAuthValidatorTest {

    @Test
    fun `유효 토큰 일치 시 true 반환`() {
        val validator = validatorWithToken("expected-token")
        assertThat(validator.isAuthorized("expected-token")).isTrue()
    }

    @Test
    fun `잘못된 토큰 시 false 반환`() {
        val validator = validatorWithToken("expected-token")
        assertThat(validator.isAuthorized("wrong-token")).isFalse()
    }

    @Test
    fun `null 토큰 시 false 반환`() {
        val validator = validatorWithToken("expected-token")
        assertThat(validator.isAuthorized(null)).isFalse()
    }

    @Test
    fun `expected 가 빈 문자열이면 어떤 토큰도 false 반환 — config 누락 시 endpoint 비활성`() {
        val validator = validatorWithToken("")
        assertThat(validator.isAuthorized("anything")).isFalse()
        assertThat(validator.isAuthorized("")).isFalse()
        assertThat(validator.isAuthorized(null)).isFalse()
    }

    private fun validatorWithToken(token: String): InternalAuthValidator =
        InternalAuthValidator(properties(token))

    private fun properties(token: String): RemoteAgentProperties = RemoteAgentProperties(
        url = "http://localhost:8081",
        connectTimeout = Duration.ofSeconds(3),
        readTimeout = Duration.ofSeconds(60),
        maxInMemorySize = DataSize.ofMegabytes(10),
        poll = PollProperties(maxAttempts = 30, interval = Duration.ofSeconds(2), timeout = Duration.ofSeconds(60)),
        callback = CallbackProperties(internalAuthToken = token),
    )
}
