package stillframe42.aicodereviewer.review.domain.model

// 코드 이슈 심각도 레벨
enum class IssueSeverity {
    CRITICAL,   // 즉시 수정 필요 (버그, 보안 취약점)
    WARNING,    // 수정 권고 (잠재적 문제)
    INFO        // 개선 제안 (가독성, 컨벤션)
}
