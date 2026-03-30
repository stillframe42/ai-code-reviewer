package stillframe42.aicodereviewer.github.adapter.out.github.dto

// GET /repos/{owner}/{repo}/commits 응답 항목 — 커밋 히스토리 조회용
data class CommitSummaryResponse(
    val sha: String,
    val commit: CommitDetail,
) {
    data class CommitDetail(
        val message: String,
        val author: CommitAuthor,
    )
    data class CommitAuthor(val name: String, val date: String)
}
