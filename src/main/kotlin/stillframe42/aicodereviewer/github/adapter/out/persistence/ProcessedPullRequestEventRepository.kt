package stillframe42.aicodereviewer.github.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedPullRequestEventRepository : JpaRepository<ProcessedPullRequestEventEntity, Long> {

    // (레포, PR번호, SHA) 조합의 처리 이력 존재 여부를 조회한다
    fun existsByRepositoryFullNameAndPullRequestNumberAndHeadSha(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
    ): Boolean

    // (레포, PR번호) 기준으로 가장 최근에 처리된 이벤트를 조회한다 — dismiss 대상 review_id 조회에 사용
    fun findTopByRepositoryFullNameAndPullRequestNumberOrderByProcessedAtDesc(
        repositoryFullName: String,
        pullRequestNumber: Int,
    ): ProcessedPullRequestEventEntity?
}
