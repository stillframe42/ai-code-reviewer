package stillframe42.aicodereviewer.github.adapter.out.github.dto

// GET /repos/{owner}/{repo}/contents/{path} 응답 DTO (디렉토리 조회 시 배열로 반환)
// sha, url 등 리뷰에 불필요한 필드는 Jackson이 자동으로 무시한다
data class DirectoryEntryResponse(
    val name: String,
    val path: String,
    val type: String,  // "file" | "dir" | "symlink"
)
