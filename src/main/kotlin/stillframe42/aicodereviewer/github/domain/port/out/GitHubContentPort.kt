package stillframe42.aicodereviewer.github.domain.port.out

import stillframe42.aicodereviewer.github.domain.model.FileCommitSummary
import stillframe42.aicodereviewer.github.domain.model.PrDescription
import stillframe42.aicodereviewer.github.domain.model.RepositoryEntry

// GitHub 레포지토리 콘텐츠 조회 출력 포트 — review 의 LLM Tool 이 소비한다
// 토큰 해석은 구현체 책임 (installationId 별 캐싱)
interface GitHubContentPort {
    // 파일 내용을 디코딩된 평문으로 반환한다
    fun getFileContent(repositoryFullName: String, path: String, ref: String, installationId: Long): String

    // path 가 빈 문자열이면 루트 디렉토리를 조회한다
    fun getDirectoryEntries(repositoryFullName: String, path: String, ref: String, installationId: Long): List<RepositoryEntry>

    fun getPrDescription(repositoryFullName: String, prNumber: Int, installationId: Long): PrDescription

    // 최근 커밋 최대 5건
    fun getFileCommitHistory(repositoryFullName: String, filePath: String, installationId: Long): List<FileCommitSummary>
}
