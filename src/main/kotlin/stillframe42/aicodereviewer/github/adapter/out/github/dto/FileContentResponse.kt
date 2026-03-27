package stillframe42.aicodereviewer.github.adapter.out.github.dto

// GET /repos/{owner}/{repo}/contents/{path} 응답 DTO
// content 필드는 MIME Base64 인코딩된 파일 내용이며 76자마다 '\n'이 삽입된다
// sha, url 등 리뷰에 불필요한 필드는 Jackson이 자동으로 무시한다
data class FileContentResponse(
    val name: String,
    val path: String,
    val size: Long,
    val content: String,   // Base64 인코딩 + 개행 포함
    val encoding: String,  // 항상 "base64"
)
