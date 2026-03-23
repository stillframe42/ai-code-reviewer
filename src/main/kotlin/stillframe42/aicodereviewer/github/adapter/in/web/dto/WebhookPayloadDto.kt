package stillframe42.aicodereviewer.github.adapter.`in`.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import stillframe42.aicodereviewer.github.domain.model.PullRequestAction
import stillframe42.aicodereviewer.github.domain.model.PullRequestEvent

// GitHub pull_request Webhook 페이로드 JSON 파싱 DTO
data class WebhookPayloadDto(
    val action: String,
    val installation: InstallationDto,
    val repository: RepositoryDto,
    @field:JsonProperty("pull_request")
    val pullRequest: PullRequestDto,
) {
    data class InstallationDto(
        val id: Long,
    )

    data class RepositoryDto(
        @field:JsonProperty("full_name")
        val fullName: String,
    )

    data class PullRequestDto(
        val number: Int,
        val head: HeadDto,
    ) {
        data class HeadDto(val sha: String)
    }

    // GitHub의 소문자 action 문자열을 도메인 모델로 변환한다
    // 지원하지 않는 action (예: "closed", "labeled")은 null 반환
    fun toDomain(): PullRequestEvent? {
        val pullRequestAction = PullRequestAction.fromString(action) ?: return null
        return PullRequestEvent(
            action = pullRequestAction,
            installationId = installation.id,
            repositoryFullName = repository.fullName,
            pullRequestNumber = pullRequest.number,
            headSha = pullRequest.head.sha,
        )
    }
}
