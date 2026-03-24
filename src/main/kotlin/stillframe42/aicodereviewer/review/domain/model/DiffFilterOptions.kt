package stillframe42.aicodereviewer.review.domain.model

// PR diff 전처리 옵션 — AI에 전달하기 전 불필요한 청크·줄을 제거하는 동작을 제어한다
data class DiffFilterOptions(
    // 테스트 파일(*Test.kt, *Spec.kt 등) 제외 여부
    val filterTestFiles: Boolean = true,
    // 잠금 파일(*.lock, package-lock.json 등) 제외 여부
    val filterLockFiles: Boolean = true,
    // 사용자 정의 추가 제외 glob 패턴 목록
    val additionalExcludePatterns: List<String> = emptyList(),
    // 변경 전후 유지할 context 줄 수 (0 = +/- 줄만, 3 = 기본값)
    val contextLines: Int = 3,
    // 최대 허용 토큰 수 — 초과 시 변경량 적은 파일 청크부터 제거 (null = 한도 없음)
    val maxTokens: Int? = null,
) {
    object Defaults {
        val TEST_FILE_PATTERNS = listOf(
            "**/*Test.kt", "**/*Spec.kt", "**/*Tests.kt",
            "**/*.test.*", "**/*.spec.*",
        )
        val LOCK_FILE_PATTERNS = listOf(
            "**/*.lock", "**/package-lock.json", "**/yarn.lock",
        )
        // 항상 제외하는 파일 패턴 (보안 민감 파일 및 AI 리뷰에 불필요한 파일)
        val DEFAULT_EXCLUDE = listOf(
            "**/.env", "**/.env.*", "**/*.snap", "**/*.min.js", "**/*.min.css",
        )
    }
}
