package stillframe42.aicodereviewer.agent.adapter.`in`.web.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AgentCallbackPayloadDtoTest {

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `snake_case JSON 이 camelCase 필드로 매핑된다`() {
        val json = """
            {
              "analysis_id": "abc-123",
              "status": "DONE",
              "issues": [{
                "severity": "HIGH",
                "type": "X",
                "location": "a:1",
                "description": "d",
                "suggestion": "s",
                "owasp_reference": "A01:2021"
              }],
              "error": null
            }
        """.trimIndent()

        val payload = mapper.readValue(json, AgentCallbackPayload::class.java)

        assertThat(payload.analysisId).isEqualTo("abc-123")
        assertThat(payload.status).isEqualTo("DONE")
        assertThat(payload.issues).hasSize(1)
        assertThat(payload.issues[0].owaspReference).isEqualTo("A01:2021")
        assertThat(payload.error).isNull()
    }

    @Test
    fun `toDomain 으로 도메인 모델 변환된다`() {
        val payload = AgentCallbackPayload(
            analysisId = "abc-123",
            status = "DONE",
            issues = listOf(
                AgentCallbackIssuePayload(
                    severity = "HIGH",
                    type = "X",
                    location = "a:1",
                    description = "d",
                    suggestion = "s",
                    owaspReference = "A01:2021",
                ),
            ),
        )

        val result = payload.toDomain()

        assertThat(result.analysisId).isEqualTo("abc-123")
        assertThat(result.issues[0].owaspReference).isEqualTo("A01:2021")
    }
}
