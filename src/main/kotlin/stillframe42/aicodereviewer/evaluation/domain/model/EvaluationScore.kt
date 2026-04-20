package stillframe42.aicodereviewer.evaluation.domain.model

data class EvaluationScore(
    val metric: EvaluationMetric,
    val score: Double,
    val reason: String,
    val details: Map<String, Any> = emptyMap(),
)
