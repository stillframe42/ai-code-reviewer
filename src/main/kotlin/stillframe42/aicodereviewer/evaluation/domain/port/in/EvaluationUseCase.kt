package stillframe42.aicodereviewer.evaluation.domain.port.`in`

import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase

interface EvaluationUseCase {
    // topK·threshold는 production 기본값 또는 실험 값을 호출자가 명시 전달한다 (default 없음 — production/eval drift 방지).
    suspend fun evaluateAll(
        cases: List<GoldenCase>,
        topK: Int,
        threshold: Double,
    ): List<EvaluationResult>
}
