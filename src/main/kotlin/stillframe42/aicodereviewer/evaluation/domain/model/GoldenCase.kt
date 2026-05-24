package stillframe42.aicodereviewer.evaluation.domain.model

data class GoldenCase(
    val id: String,
    val category: String,
    val description: String,
    val patchFile: String,
    val expectedIssues: List<String>,
    val relevantConvention: String,
)
