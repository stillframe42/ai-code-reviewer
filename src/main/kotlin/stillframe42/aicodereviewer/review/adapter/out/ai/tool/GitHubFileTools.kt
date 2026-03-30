package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

// Spring AI Tool Calling용 GitHub 파일 관련 도구 모음
// LLM이 코드 리뷰 중 파일 내용 및 관련 파일 목록을 조회할 수 있도록 @Tool 메서드를 제공한다
// @Tool 메서드는 suspend 불가 — runBlocking(Dispatchers.IO)으로 코루틴 브릿지
// 오류 발생 시 예외를 던지지 않고 LLM이 읽을 수 있는 오류 메시지 문자열을 반환한다
@Component
class GitHubFileTools(
    private val gitHubHttpClient: GitHubHttpClient,
    private val tokenPort: GitHubTokenPort,
) : Logging {

    // installationId는 ToolContext로 전달 — LLM 스키마에 노출되지 않으며 호출자가 주입한다
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

    // sameDir: filePath의 상위 디렉토리 (예: "src/foo/Bar.kt" → "src/foo")
    // parentDir: sameDir의 상위 디렉토리 (예: "src/foo" → "src")
    // 루트 파일("Bar.kt")이면 sameDir == parentDir == "" 이므로 상위 조회를 생략한다
    // sameDir과 parentDir을 async로 병렬 조회하여 응답 시간을 단축한다
    @Tool(description = "리뷰 중인 파일과 관련된 다른 파일을 조회합니다")
    fun getRelatedFile(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") filePath: String,
        @ToolParam(description = "브랜치명 또는 커밋 SHA") ref: String,
        toolContext: ToolContext,
    ): String = runBlocking(Dispatchers.IO) {
        val installationId = toolContext.context["installationId"] as? Long
            ?: error("ToolContext에 installationId가 없습니다")
        logger.info(
            "getRelatedFile 호출: repository={}, filePath={}, ref={}, installationId={}",
            repositoryFullName, filePath, ref, installationId,
        )
        runCatching {
            val token = tokenPort.getInstallationToken(installationId)
            val sameDir = filePath.substringBeforeLast("/", missingDelimiterValue = "")
            val parentDir = sameDir.substringBeforeLast("/", missingDelimiterValue = "")

            val sameDirJob = async { gitHubHttpClient.fetchDirectoryContents(repositoryFullName, sameDir, ref, token) }
            val parentDirJob = if (sameDir != parentDir) {
                async { gitHubHttpClient.fetchDirectoryContents(repositoryFullName, parentDir, ref, token) }
            } else null

            buildString {
                val sameFiles = sameDirJob.await().filter { it.type == "file" && it.path != filePath }
                val dirLabel = sameDir.ifEmpty { "(루트)" }
                appendLine("[같은 디렉토리: $dirLabel]")
                if (sameFiles.isEmpty()) appendLine("(파일 없음)")
                else sameFiles.forEach { appendLine("- ${it.path}") }

                if (parentDirJob != null) {
                    appendLine()
                    val parentFiles = parentDirJob.await().filter { it.type == "file" && it.path != filePath }
                    val parentLabel = parentDir.ifEmpty { "(루트)" }
                    appendLine("[상위 디렉토리: $parentLabel]")
                    if (parentFiles.isEmpty()) appendLine("(파일 없음)")
                    else parentFiles.forEach { appendLine("- ${it.path}") }
                }
            }.trimEnd()
        }.getOrElse { e ->
            when (e) {
                is WebClientResponseException.NotFound ->
                    "디렉토리를 찾을 수 없습니다: $filePath"
                is WebClientResponseException ->
                    "GitHub API 오류 (${e.statusCode}): ${e.message}"
                else -> {
                    logger.warn("getRelatedFile 실패: repository={}, filePath={}", repositoryFullName, filePath, e)
                    "관련 파일을 가져오는 중 오류가 발생했습니다: ${e.message}"
                }
            }
        }
    }
}
