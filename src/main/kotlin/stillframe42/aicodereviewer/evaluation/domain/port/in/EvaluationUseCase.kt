package stillframe42.aicodereviewer.evaluation.domain.port.`in`

import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase

interface EvaluationUseCase {
    // topK/threshold는 sweep 실험용 — 호출자가 production 기본값 또는 실험 값을 명시적으로 전달한다.
    // default를 제거한 이유: 과거 default (topK=5, threshold=0.0) 가 production (topK=3, threshold=0.7) 과
    // drift 되어 기본값에 의존한 호출이 production과 다른 경로로 측정하는 위험이 있었다. (Sub-plan B2-2)
    suspend fun evaluateAll(
        cases: List<GoldenCase>,
        topK: Int,
        threshold: Double,
    ): List<EvaluationResult>
}
