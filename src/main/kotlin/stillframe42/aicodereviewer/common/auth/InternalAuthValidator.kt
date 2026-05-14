package stillframe42.aicodereviewer.common.auth

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.config.PythonAgentProperties

// 내부 endpoint (AgentCallbackController, RagContextController) 에서 X-Internal-Auth 헤더 토큰을 검증.
// 빈 expected = config 누락 = 모든 요청 거부 (endpoint 사실상 비활성).
@Component
class InternalAuthValidator(
    private val properties: PythonAgentProperties,
) {
    fun isAuthorized(token: String?): Boolean {
        val expected = properties.callback.internalAuthToken
        return expected.isNotEmpty() && token == expected
    }
}
