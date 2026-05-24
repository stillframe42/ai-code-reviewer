package stillframe42.aicodereviewer.agent.domain.port.`in`

import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.review.domain.model.CodeReview

// 보안 PR 의 원격 에이전트 분석 진입점 — Spring Boot 측 webhook 흐름이 이 인터페이스를 통해 agent 경로로 위임한다.
interface AgentReviewUseCase {
    suspend fun review(
        repositoryFullName: String,
        pullRequestNumber: Int,
        prDiff: String,
        prFiles: List<PrFile>,
        reviewRequestId: Long?,
    ): CodeReview
}
