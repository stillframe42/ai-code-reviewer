package stillframe42.aicodereviewer.agent.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import stillframe42.aicodereviewer.agent.domain.model.AgentCallbackResult
import stillframe42.aicodereviewer.agent.domain.port.`in`.AgentCallbackUseCase
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

@Import(SpyCallbackConfig::class)
class AgentCallbackControllerIntegrationTest : AbstractIntegrationTest() {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun callbackOverrides(registry: DynamicPropertyRegistry) {
            registry.add("agent.remote.callback.internal-auth-token") { "test-internal-token" }
        }
    }

    @Autowired
    private lateinit var spyHandler: SpyAgentCallbackHandler

    @AfterEach
    fun resetSpy() {
        spyHandler.invocations.clear()
    }

    private val sampleBody = """
        {
          "analysis_id": "cb-001",
          "status": "DONE",
          "issues": [{
            "severity": "HIGH",
            "type": "WEAK_HASHING",
            "location": "SecurityConfig.kt:12",
            "description": "MD5",
            "suggestion": "bcrypt",
            "owasp_reference": "A02:2021"
          }],
          "error": null
        }
    """.trimIndent()

    @Test
    fun `유효 토큰 + 정상 본문이면 202 + handler 호출 + 페이로드 매핑`() {
        client.post().uri("/internal/agent/callback")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Internal-Auth", "test-internal-token")
            .body(sampleBody)
            .exchange()
            .expectStatus().isAccepted

        assertThat(spyHandler.invocations).hasSize(1)
        val received = spyHandler.invocations[0]
        assertThat(received.analysisId).isEqualTo("cb-001")
        assertThat(received.status).isEqualTo("DONE")
        assertThat(received.issues).hasSize(1)
        assertThat(received.issues[0].owaspReference).isEqualTo("A02:2021")
    }

    @Test
    fun `토큰 헤더 누락이면 401 + handler 미호출`() {
        client.post().uri("/internal/agent/callback")
            .contentType(MediaType.APPLICATION_JSON)
            .body(sampleBody)
            .exchange()
            .expectStatus().isUnauthorized

        assertThat(spyHandler.invocations).isEmpty()
    }

    @Test
    fun `토큰 헤더 불일치면 401 + handler 미호출`() {
        client.post().uri("/internal/agent/callback")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Internal-Auth", "wrong-token")
            .body(sampleBody)
            .exchange()
            .expectStatus().isUnauthorized

        assertThat(spyHandler.invocations).isEmpty()
    }
}

@TestConfiguration
class SpyCallbackConfig {
    @Bean
    @Primary
    fun spyCallbackHandler(): AgentCallbackUseCase = SpyAgentCallbackHandler()
}

class SpyAgentCallbackHandler : AgentCallbackUseCase {
    val invocations = mutableListOf<AgentCallbackResult>()
    override suspend fun handle(result: AgentCallbackResult) {
        invocations.add(result)
    }
}
