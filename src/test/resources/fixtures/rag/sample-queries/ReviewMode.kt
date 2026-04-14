package stillframe42.aicodereviewer.review.domain.model

// 코드 리뷰 실행 모드 — Tool Calling 사용 여부와 필요한 컨텍스트를 타입 수준에서 명시한다
sealed interface ReviewMode {

    // Tool 없이 diff 텍스트만으로 리뷰
    data object Simple : ReviewMode

    // GitHub Tool Calling 활성화 — LLM이 파일 내용을 직접 조회할 수 있다
    data class WithGitHubTools(val installationId: Long) : ReviewMode
}
