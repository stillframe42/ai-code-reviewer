package stillframe42.aicodereviewer.evaluation.domain.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GoldenCaseParsingTest {

    // fixture JSON 의 snake_case 키 ↔ GoldenCase 의 camelCase property 매핑은 ObjectMapper 에 위임
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @Test
    fun `golden-dataset json을 GoldenCase 리스트로 파싱한다`() {
        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader()
            .readText()

        val root = objectMapper.readTree(json)
        val cases: List<GoldenCase> = objectMapper.readValue(root["cases"].toString())

        assertThat(cases).hasSize(20)
        assertThat(cases.map { it.category }.distinct()).containsExactlyInAnyOrder(
            "SECURITY", "ARCH", "STYLE", "API"
        )

        val sec001 = cases.first { it.id == "SEC-001" }
        assertThat(sec001.patchFile).isEqualTo("patches/SEC-001-sql-injection.patch")
        assertThat(sec001.expectedIssues).containsExactly("SQL Injection 취약점 — 파라미터 바인딩 미사용")
        assertThat(sec001.relevantConvention).isEqualTo("security-checklist.md#A03")
    }

    @Test
    fun `각 케이스의 patch_file이 실제 파일을 참조한다`() {
        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader()
            .readText()

        val root = objectMapper.readTree(json)
        val cases: List<GoldenCase> = objectMapper.readValue(root["cases"].toString())

        cases.forEach { case ->
            val patchStream = javaClass.classLoader
                .getResourceAsStream("fixtures/evaluation/${case.patchFile}")
            assertThat(patchStream)
                .describedAs("${case.id}의 patch 파일이 존재해야 함: ${case.patchFile}")
                .isNotNull()
        }
    }
}
