package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.github.adapter.out.github.client.GitHubHttpClient
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort
import java.util.Base64

// Spring AI Tool Calling용 GitHub API 도구 모음
// LLM이 코드 리뷰 중 GitHub 파일 내용을 직접 조회할 수 있도록 @Tool 메서드를 제공한다
@Component
class GitHubTools(
    private val gitHubHttpClient: GitHubHttpClient,
    private val tokenPort: GitHubTokenPort,
) : Logging {

    // GitHub Contents API로 파일 내용을 조회하고 Base64 디코딩하여 반환한다
    // @Tool 메서드는 suspend 불가 — runBlocking(Dispatchers.IO)으로 코루틴 브릿지
    // installationId는 ToolContext로 전달 — LLM 스키마에 노출되지 않으며 호출자가 주입한다
    // 오류 발생 시 예외를 던지지 않고 LLM이 읽을 수 있는 오류 메시지 문자열을 반환한다
    @Tool(description = "특정 파일의 전체 내용을 가져옵니다")
    fun getFileContent(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") path: String,
        @ToolParam(description = "브랜치명 또는 커밋 SHA") ref: String,
        toolContext: ToolContext,
    ): String = runBlocking(Dispatchers.IO) {
        val installationId = toolContext.context["installationId"] as? Long
            ?: error("ToolContext에 installationId가 없습니다")
        logger.info(
            "getFileContent 호출: repository={}, path={}, ref={}, installationId={}",
            repositoryFullName, path, ref, installationId,
        )
        runCatching {
            val token = tokenPort.getInstallationToken(installationId)
            val response = gitHubHttpClient.fetchFileContent(repositoryFullName, path, ref, token)
            Base64.getMimeDecoder().decode(response.content).toString(Charsets.UTF_8)
        }.getOrElse { e ->
            when (e) {
                is WebClientResponseException.NotFound ->
                    "파일을 찾을 수 없습니다: $path (ref=$ref)"
                is WebClientResponseException ->
                    "GitHub API 오류 (${e.statusCode}): ${e.message}"
                else -> {
                    logger.warn("getFileContent 실패: repository={}, path={}", repositoryFullName, path, e)
                    "파일 내용을 가져오는 중 오류가 발생했습니다: ${e.message}"
                }
            }
        }
    }
}
