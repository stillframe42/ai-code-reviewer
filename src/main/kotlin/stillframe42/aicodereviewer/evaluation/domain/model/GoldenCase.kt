package stillframe42.aicodereviewer.evaluation.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

data class GoldenCase(
    val id: String,
    val category: String,
    val description: String,
    @field:JsonProperty("patch_file")
    val patchFile: String,
    @field:JsonProperty("expected_issues")
    val expectedIssues: List<String>,
    @field:JsonProperty("relevant_convention")
    val relevantConvention: String,
)
