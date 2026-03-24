package stillframe42.aicodereviewer.github.adapter.out.github.dto

import com.fasterxml.jackson.annotation.JsonProperty
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrFileStatus

// GET /repos/{owner}/{repo}/pulls/{number}/files 응답의 단일 파일 항목 DTO
data class PrFileResponse(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null,
    @param:JsonProperty("previous_filename") val previousFilename: String? = null,
) {
    // GitHub API 응답 DTO를 도메인 모델로 변환한다
    fun toDomain(): PrFile = PrFile(
        filename = filename,
        status = PrFileStatus.from(status),
        additions = additions,
        deletions = deletions,
        changes = changes,
        patch = patch,
        previousFilename = previousFilename,
    )
}
