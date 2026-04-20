package stillframe42.aicodereviewer.evaluation.domain.model

import java.time.Instant

data class EvaluationResult(
    val caseId: String,
    val scores: List<EvaluationScore>,
    val executedAt: Instant,
)
