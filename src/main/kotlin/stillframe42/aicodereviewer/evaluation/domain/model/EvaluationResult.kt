package stillframe42.aicodereviewer.evaluation.domain.model

import java.time.Instant

data class EvaluationResult(
    val caseId: String,
    val scores: List<EvaluationScore>,
    val executedAt: Instant,
    // 컨텍스트 토큰 추정값 — retrievedDocs 텍스트 길이 / 4 (sweep 비교용 상대 지표)
    val contextTokenEstimate: Int = 0,
)
