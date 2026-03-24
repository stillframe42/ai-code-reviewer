package stillframe42.aicodereviewer.review.domain.model

// diff 전처리 결과 — 처리된 diff 문자열과 토큰 절감 통계를 함께 반환한다
data class DiffPreprocessResult(
    // 파일별 개별 diff 청크 목록 — 병렬 LLM 호출에 사용
    val fileDiffs: List<String>,
    val estimatedTokensBefore: Int,
    val estimatedTokensAfter: Int,
    // 전처리 중 제거된 파일 목록
    val filteredFiles: List<String>,
) {
    // 모든 파일 청크를 합친 combined diff — 단일 호출 경로 하위 호환용
    val diff: String get() = fileDiffs.joinToString("\n")
    val savedTokens: Int get() = estimatedTokensBefore - estimatedTokensAfter
    val reductionPercent: Int get() = if (estimatedTokensBefore > 0)
        (savedTokens * 100 / estimatedTokensBefore) else 0
}
