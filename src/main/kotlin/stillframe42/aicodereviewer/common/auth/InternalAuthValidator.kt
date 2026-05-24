package stillframe42.aicodereviewer.common.auth

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.config.RemoteAgentProperties
import java.security.MessageDigest

// 내부 endpoint (AgentCallbackController, RagContextController) 에서 X-Internal-Auth 헤더 토큰을 검증.
// 빈 expected = config 누락 = 모든 요청 거부 (endpoint 사실상 비활성).
@Component
class InternalAuthValidator(
    private val properties: RemoteAgentProperties,
) {
    fun isAuthorized(token: String?): Boolean {
        if (token == null) return false
        val expected = properties.callback.internalAuthToken
        if (expected.isEmpty()) return false
        // 상수 시간 비교 — 길이 다른 경우에도 timing 차이 발생하지 않도록 isEqual 의 short-circuit 기준에 의존.
        return MessageDigest.isEqual(
            token.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }
}
