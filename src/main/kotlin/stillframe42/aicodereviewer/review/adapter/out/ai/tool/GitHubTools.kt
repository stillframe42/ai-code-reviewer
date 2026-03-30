package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import kotlinx.coroutines.CoroutineScope
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
import stillframe42.aicodereviewer.github.adapter.out.github.dto.DirectoryEntryResponse
import stillframe42.aicodereviewer.github.adapter.out.github.ratelimit.GitHubRateLimitChecker
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort
import java.util.Base64

@Component
class GitHubTools(
    private val gitHubHttpClient: GitHubHttpClient,
    private val tokenPort: GitHubTokenPort,
    private val toolCallLogger: ToolCallLogger,
    private val rateLimitChecker: GitHubRateLimitChecker,
) : Logging {

    @Tool(description = "특정 파일의 전체 내용을 가져옵니다")
    fun getFileContent(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") path: String,
        @ToolParam(description = "브랜치명 또는 커밋 SHA") ref: String,
        toolContext: ToolContext,
    ): String = executeToolCall(
        toolContext = toolContext,
        toolName = "getFileContent",
        argsLog = "repo=$repositoryFullName, path=$path, ref=$ref",
        notFoundMessage = "파일을 찾을 수 없습니다: $path (ref=$ref)",
        fallbackMessage = "파일 내용을 가져오는 중 오류가 발생했습니다",
    ) { token, installationId ->
        gitHubHttpClient.fetchFileContent(repositoryFullName, path, ref, token, installationId)
            .let { Base64.getMimeDecoder().decode(it.content).toString(Charsets.UTF_8) }
    }

    @Tool(description = "리뷰 중인 파일과 관련된 다른 파일을 조회합니다")
    fun getRelatedFile(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") filePath: String,
        @ToolParam(description = "브랜치명 또는 커밋 SHA") ref: String,
        toolContext: ToolContext,
    ): String = executeToolCall(
        toolContext = toolContext,
        toolName = "getRelatedFile",
        argsLog = "repo=$repositoryFullName, filePath=$filePath, ref=$ref",
        notFoundMessage = "디렉토리를 찾을 수 없습니다: ${filePath.substringBeforeLast("/", missingDelimiterValue = "(루트)")}",
        fallbackMessage = "관련 파일을 가져오는 중 오류가 발생했습니다",
    ) { token, installationId ->
        val sameDir = filePath.substringBeforeLast("/", missingDelimiterValue = "")
        val parentDir = sameDir.substringBeforeLast("/", missingDelimiterValue = "")
        val sameDirJob = async { gitHubHttpClient.fetchDirectoryContents(repositoryFullName, sameDir, ref, token, installationId) }
        val parentDirJob = if (sameDir != parentDir) {
            async { gitHubHttpClient.fetchDirectoryContents(repositoryFullName, parentDir, ref, token, installationId) }
        } else null
        formatRelatedFiles(filePath, sameDir, sameDirJob.await(), parentDir, parentDirJob?.await())
    }

    @Tool(description = "PR의 제목과 설명을 가져옵니다")
    fun getPRDescription(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "PR 번호") prNumber: Int,
        toolContext: ToolContext,
    ): String = executeToolCall(
        toolContext = toolContext,
        toolName = "getPRDescription",
        argsLog = "repo=$repositoryFullName, prNumber=$prNumber",
        notFoundMessage = "PR을 찾을 수 없습니다: #$prNumber",
        fallbackMessage = "PR 설명을 가져오는 중 오류가 발생했습니다",
    ) { token, installationId ->
        val response = gitHubHttpClient.fetchPrDescription(repositoryFullName, prNumber, token, installationId)
        val body = response.body?.takeIf { it.isNotBlank() } ?: "(설명 없음)"
        "제목: ${response.title}\n설명: $body"
    }

    @Tool(description = "해당 파일의 최근 커밋 이력 최대 5건을 가져옵니다")
    fun getFileHistory(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") filePath: String,
        toolContext: ToolContext,
    ): String = executeToolCall(
        toolContext = toolContext,
        toolName = "getFileHistory",
        argsLog = "repo=$repositoryFullName, filePath=$filePath",
        notFoundMessage = "파일을 찾을 수 없습니다: $filePath",
        fallbackMessage = "커밋 이력을 가져오는 중 오류가 발생했습니다",
    ) { token, installationId ->
        val commits = gitHubHttpClient.fetchFileCommitHistory(repositoryFullName, filePath, token, installationId)
        if (commits.isEmpty()) return@executeToolCall "커밋 이력이 없습니다: $filePath"
        commits.mapIndexed { index, commit ->
            val shortSha = commit.sha.take(7)
            val message = commit.commit.message.lines().first()
            val author = commit.commit.author.name
            val date = commit.commit.author.date.take(10)
            "[${index + 1}] $shortSha — $message\n    작성자: $author | $date"
        }.joinToString("\n")
    }

    private fun formatRelatedFiles(
        filePath: String,
        sameDir: String,
        sameDirEntries: List<DirectoryEntryResponse>,
        parentDir: String,
        parentDirEntries: List<DirectoryEntryResponse>?,
    ): String = buildString {
        appendDirectorySection("[같은 디렉토리: ${sameDir.ifEmpty { "(루트)" }}]", sameDirEntries, filePath)
        if (parentDirEntries != null) {
            appendLine()
            appendDirectorySection("[상위 디렉토리: ${parentDir.ifEmpty { "(루트)" }}]", parentDirEntries, filePath)
        }
    }.trimEnd()

    private fun StringBuilder.appendDirectorySection(
        label: String,
        entries: List<DirectoryEntryResponse>,
        excludePath: String,
    ) {
        appendLine(label)
        val files = entries.filter { it.type == "file" && it.path != excludePath }
        if (files.isEmpty()) appendLine("(파일 없음)")
        else files.forEach { appendLine("- ${it.path}") }
    }

    // 모든 Tool 메서드의 공통 골격 — Rate Limit 체크, 로깅, 에러 처리를 한 곳에서 관리한다
    // CoroutineScope 수신자: getRelatedFile의 async { } 호출을 위해 block에 CoroutineScope를 전달한다
    private fun executeToolCall(
        toolContext: ToolContext,
        toolName: String,
        argsLog: String,
        notFoundMessage: String,
        fallbackMessage: String,
        block: suspend CoroutineScope.(token: String, installationId: Long) -> String,
    ): String {
        val installationId = toolContext.installationId()
        rateLimitChecker.checkOrNull(installationId)?.let { return it }
        return toolCallLogger.log(toolName, argsLog) {
            runBlocking(Dispatchers.IO) {
                logger.info("{} 호출: {}", toolName, argsLog)
                runCatching {
                    val token = tokenPort.getInstallationToken(installationId)
                    block(token, installationId)
                }.getOrElse { e ->
                    when (e) {
                        is WebClientResponseException.NotFound -> notFoundMessage
                        is WebClientResponseException ->
                            "GitHub API 오류 (${e.statusCode}): ${e.message}"
                        else -> {
                            logger.warn("{} 실패", toolName, e)
                            "$fallbackMessage: ${e.message}"
                        }
                    }
                }
            }
        }
    }

    private fun ToolContext.installationId(): Long =
        context["installationId"] as? Long ?: error("ToolContext에 installationId가 없습니다")
}
