package stillframe42.aicodereviewer.agent.adapter.`in`.web.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AgentCallbackResultDtoTest {

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

        val result = mapper.readValue(json, AgentCallbackResult::class.java)

        assertThat(result.analysisId).isEqualTo("abc-123")
        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.issues).hasSize(1)
        assertThat(result.issues[0].owaspReference).isEqualTo("A01:2021")
        assertThat(result.error).isNull()
    }
}
