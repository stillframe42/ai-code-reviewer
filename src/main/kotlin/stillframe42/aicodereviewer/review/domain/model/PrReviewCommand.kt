package stillframe42.aicodereviewer.review.domain.model

import stillframe42.aicodereviewer.github.domain.model.PrFile

// PR 리뷰 오케스트레이션 입력 — 웹훅 이벤트에서 리뷰 파이프라인에 필요한 값만 추린 커맨드
data class PrReviewCommand(
    val repositoryFullName: String,
    val pullRequestNumber: Int,
    val headSha: String,
    val installationId: Long,
    val prDiff: String,
    val prFiles: List<PrFile>,
)
