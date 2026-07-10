package stillframe42.aicodereviewer.github.domain.model

// 레포지토리 디렉토리 항목 — GitHub contents API 의 file/dir/symlink/submodule 을 도메인 타입으로 표현
data class RepositoryEntry(
    val path: String,
    val type: RepositoryEntryType,
)

enum class RepositoryEntryType {
    FILE, DIR, OTHER
}
