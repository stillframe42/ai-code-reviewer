package stillframe42.aicodereviewer.evaluation.domain.port.`in`

import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase

interface EvaluationUseCase {
    suspend fun evaluateAll(cases: List<GoldenCase>): List<EvaluationResult>
}
