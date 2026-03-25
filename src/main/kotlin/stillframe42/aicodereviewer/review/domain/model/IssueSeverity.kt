package stillframe42.aicodereviewer.review.domain.model

// 코드 이슈 심각도 레벨
enum class IssueSeverity {
    CRITICAL,   // 운영 장애 / 보안 취약점 — 즉시 수정 필수
    MAJOR,      // 잠재적 버그 / 성능 저하 — 머지 전 수정 권고
    MINOR,      // 컨벤션 / 가독성 — 수정 권장
    SUGGESTION; // 선택적 개선 / 리팩토링 아이디어

    // 심각도의 시각적 표현 이모지
    val emoji: String get() = when (this) {
        CRITICAL   -> "🔴"
        MAJOR      -> "🟠"
        MINOR      -> "🟡"
        SUGGESTION -> "🔵"
    }
}
