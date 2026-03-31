package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
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
import java.util.concurrent.atomic.AtomicInteger

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

    // 모든 Tool 메서드의 공통 골격 — Rate Limit 체크, 횟수 제한, 로깅, 에러 처리를 한 곳에서 관리한다
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

        // Tool 호출 횟수 추적 — Simple 모드에서는 카운터가 없으므로 null 허용
        val counter = toolContext.toolCallCounter()
        val count = counter?.incrementAndGet() ?: 0
        if (count > MAX_TOOL_CALLS) {
            logger.warn("Tool 호출 한도 초과: {}회 > {}회 (toolName={})", count, MAX_TOOL_CALLS, toolName)
            return "[ERROR] Tool 호출 한도(${MAX_TOOL_CALLS}회) 초과 — LLM이 너무 많은 Tool을 요청했습니다"
        }
        if (count >= WARN_TOOL_CALLS) {
            logger.warn("Tool 호출 횟수 경고: {}회 / 최대 {}회 (toolName={})", count, MAX_TOOL_CALLS, toolName)
        }

        return toolCallLogger.log(toolName, argsLog) {
            runBlocking(Dispatchers.IO) {
                logger.info("{} 호출 ({}번째): {}", toolName, count, argsLog)
                runCatching {
                    val token = tokenPort.getInstallationToken(installationId)
                    withTimeout(TOOL_CALL_TIMEOUT) {
                        block(token, installationId)
                    }
                }.getOrElse { e ->
                    when (e) {
                        is WebClientResponseException.NotFound -> notFoundMessage
                        is WebClientResponseException ->
                            "GitHub API 오류 (${e.statusCode}): ${e.message}"
                        else -> {
                            logger.warn("{} 실패", toolName, e)
                            "[ERROR] $toolName 실패: ${e.message}"
                        }
                    }
                }
            }
        }
    }

    private fun ToolContext.installationId(): Long =
        context["installationId"] as? Long ?: error("ToolContext에 installationId가 없습니다")

    private fun ToolContext.toolCallCounter(): AtomicInteger? =
        context["toolCallCounter"] as? AtomicInteger

    companion object {
        private const val MAX_TOOL_CALLS = 5           // 최대 Tool 호출 횟수 — 테스트 후 조정 예정
        private const val WARN_TOOL_CALLS = 3          // 경고 로그 임계값
        private val TOOL_CALL_TIMEOUT = 10.seconds     // Tool 호출 당 타임아웃 (GitHub API hang 방지)
    }
}
