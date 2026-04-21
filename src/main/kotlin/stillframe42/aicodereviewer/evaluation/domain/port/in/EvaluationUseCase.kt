package stillframe42.aicodereviewer.evaluation.domain.port.`in`

import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase

interface EvaluationUseCase {
    // topK/threshold는 sweep 실험용 — default 값은 프로덕션 기본값과 일치
    suspend fun evaluateAll(
        cases: List<GoldenCase>,
        topK: Int = 5,
        threshold: Double = 0.0,
    ): List<EvaluationResult>
}
