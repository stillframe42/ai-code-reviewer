package stillframe42.aicodereviewer.github.domain.port.out

// 이벤트 처리 이력을 조회/기록하는 아웃바운드 포트 — 중복 처리 방지에 사용된다
interface ProcessedEventPort {

    // 해당 (레포, PR번호, SHA) 조합이 이미 처리되었는지 확인한다
    suspend fun isAlreadyProcessed(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
    ): Boolean

    // 이벤트 처리 완료를 기록한다
    suspend fun markAsProcessed(
        repositoryFullName: String,
        pullRequestNumber: Int,
        headSha: String,
    )
}
