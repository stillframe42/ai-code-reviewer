package stillframe42.aicodereviewer.agent.adapter.`in`.web.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentCallbackResultDtoTest {

    private val mapper = jacksonObjectMapper()

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

        val result: AgentCallbackResult = mapper.readValue(json)

        assertThat(result.analysisId).isEqualTo("abc-123")
        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.issues).hasSize(1)
        assertThat(result.issues[0].owaspReference).isEqualTo("A01:2021")
        assertThat(result.error).isNull()
    }
}
