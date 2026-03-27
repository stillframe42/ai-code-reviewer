package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.github.adapter.out.github.client.GitHubHttpClient
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort

// Spring AI Tool Calling용 GitHub API 도구 모음
// LLM이 코드 리뷰 중 GitHub 파일 내용을 직접 조회할 수 있도록 @Tool 메서드를 제공한다
@Component
class GitHubTools(
    private val gitHubHttpClient: GitHubHttpClient,
    private val tokenPort: GitHubTokenPort,
) : Logging {

    // Phase 3에서 구현: GitHub Contents API 호출 + Base64 디코딩
    @Tool(description = "특정 파일의 전체 내용을 가져옵니다")
    fun getFileContent(
        @ToolParam(description = "레포지토리 소유자 (예: octocat)") owner: String,
        @ToolParam(description = "레포지토리 이름 (예: my-repo)") repo: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") path: String,
        @ToolParam(description = "브랜치명 또는 커밋 SHA") ref: String,
        @ToolParam(description = "GitHub App Installation ID") installationId: Long,
    ): String {
        TODO("Phase 3에서 구현 예정")
    }
}
