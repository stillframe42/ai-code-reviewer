package stillframe42.aicodereviewer.agent.adapter.out.python.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentDtoTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `AgentAnalysisRequest 직렬화 시 snake_case 키로 변환된다`() {
        val request = AgentAnalysisRequest(
            prNumber = 42,
            repo = "owner/repo",
            diff = "diff --git a/foo b/foo",
            contextIds = listOf("convention-1"),
            analysisType = "SECURITY",
            sessionId = "session-123",
        )

        val json = mapper.writeValueAsString(request)
        val tree = mapper.readTree(json)

        assertThat(tree.fieldNames().asSequence().toList()).containsExactlyInAnyOrder(
            "pr_number",
            "repo",
            "diff",
            "context_ids",
            "analysis_type",
            "session_id",
        )
        assertThat(tree.get("pr_number").asInt()).isEqualTo(42)
        assertThat(tree.get("context_ids").get(0).asText()).isEqualTo("convention-1")
        assertThat(tree.get("analysis_type").asText()).isEqualTo("SECURITY")
        assertThat(tree.get("session_id").asText()).isEqualTo("session-123")
    }

    @Test
    fun `snake_case JSON 을 AgentAnalysisResponse 로 역직렬화하면 중첩 필드까지 매핑된다`() {
        val json = """
            {
              "analysis_id": "analysis-001",
              "status": "DONE",
              "issues": [
                {
                  "severity": "HIGH",
                  "type": "SQL_INJECTION",
                  "location": "src/Foo.kt:42",
                  "description": "사용자 입력이 검증 없이 SQL 에 삽입됨",
                  "suggestion": "PreparedStatement 사용",
                  "owasp_reference": "A03:2021"
                }
              ],
              "error": null
            }
        """.trimIndent()

        val response = mapper.readValue<AgentAnalysisResponse>(json)

        assertThat(response.analysisId).isEqualTo("analysis-001")
        assertThat(response.status).isEqualTo("DONE")
        assertThat(response.error).isNull()
        assertThat(response.issues).hasSize(1)

        val issue = response.issues[0]
        assertThat(issue.severity).isEqualTo("HIGH")
        assertThat(issue.type).isEqualTo("SQL_INJECTION")
        assertThat(issue.location).isEqualTo("src/Foo.kt:42")
        assertThat(issue.description).isEqualTo("사용자 입력이 검증 없이 SQL 에 삽입됨")
        assertThat(issue.suggestion).isEqualTo("PreparedStatement 사용")
        assertThat(issue.owaspReference).isEqualTo("A03:2021")
    }

    @Test
    fun `옵셔널 필드와 기본값이 round-trip 후 보존된다`() {
        val originalRequest = AgentAnalysisRequest(
            prNumber = 1,
            repo = "owner/repo",
            diff = "diff",
        )

        val roundTrippedRequest: AgentAnalysisRequest =
            mapper.readValue(mapper.writeValueAsString(originalRequest))

        assertThat(roundTrippedRequest).isEqualTo(originalRequest)
        assertThat(roundTrippedRequest.contextIds).isEmpty()
        assertThat(roundTrippedRequest.analysisType).isEqualTo("GENERAL")
        assertThat(roundTrippedRequest.sessionId).isNull()

        val originalResponse = AgentAnalysisResponse(
            analysisId = "id-1",
            status = "PENDING",
        )

        val roundTrippedResponse: AgentAnalysisResponse =
            mapper.readValue(mapper.writeValueAsString(originalResponse))

        assertThat(roundTrippedResponse).isEqualTo(originalResponse)
        assertThat(roundTrippedResponse.issues).isEmpty()
        assertThat(roundTrippedResponse.error).isNull()

        val originalIssue = AgentIssue(
            severity = "LOW",
            type = "STYLE",
            location = "src/Bar.kt:10",
            description = "스타일 위반",
            suggestion = "ktlint 적용",
        )

        val roundTrippedIssue: AgentIssue =
            mapper.readValue(mapper.writeValueAsString(originalIssue))

        assertThat(roundTrippedIssue).isEqualTo(originalIssue)
        assertThat(roundTrippedIssue.owaspReference).isNull()
    }
}
