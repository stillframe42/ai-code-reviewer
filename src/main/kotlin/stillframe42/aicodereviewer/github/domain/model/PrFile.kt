package stillframe42.aicodereviewer.github.domain.model

// GitHub PR에서 변경된 단일 파일의 도메인 모델
data class PrFile(
    val filename: String,
    val status: PrFileStatus,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?,             // 바이너리 파일이거나 변경 내용이 없으면 null
    val previousFilename: String?,  // RENAMED 상태일 때 이전 파일 경로
)
