package stillframe42.aicodereviewer.github.domain.model

// GitHub PR 변경 파일의 상태 — GitHub API status 필드 값과 대응
enum class PrFileStatus {
    ADDED,
    MODIFIED,
    REMOVED,
    RENAMED,
    COPIED,
    CHANGED,
    UNCHANGED;

    companion object {
        // GitHub API 응답 문자열을 enum으로 변환한다. 알 수 없는 값은 MODIFIED로 처리
        fun from(value: String): PrFileStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MODIFIED
    }
}
