package stillframe42.aicodereviewer.review.domain.model

// diff 전처리 결과 — 처리된 diff 문자열과 토큰 절감 통계를 함께 반환한다
data class DiffPreprocessResult(
    val diff: String,
    val estimatedTokensBefore: Int,
    val estimatedTokensAfter: Int,
    // 전처리 중 제거된 파일 목록
    val filteredFiles: List<String>,
) {
    val savedTokens: Int get() = estimatedTokensBefore - estimatedTokensAfter
    val reductionPercent: Int get() = if (estimatedTokensBefore > 0)
        (savedTokens * 100 / estimatedTokensBefore) else 0
}
