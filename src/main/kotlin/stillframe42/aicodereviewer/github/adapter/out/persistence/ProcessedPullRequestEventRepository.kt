package stillframe42.aicodereviewer.github.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedPullRequestEventRepository : JpaRepository<ProcessedPullRequestEventEntity, Long> {

    // (레포, PR번호, SHA) 조합의 처리 이력 존재 여부를 조회한다
    fun existsByRepositoryFullNameAndPullRequestNumberAndHeadSha(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
    ): Boolean
}
