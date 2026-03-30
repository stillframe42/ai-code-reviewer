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
import stillframe42.aicodereviewer.github.adapter.out.github.dto.DirectoryEntryResponse
import stillframe42.aicodereviewer.github.domain.port.out.GitHubTokenPort
import java.util.Base64

@Component
class GitHubTools(
    private val gitHubHttpClient: GitHubHttpClient,
    private val tokenPort: GitHubTokenPort,
    private val toolCallLogger: ToolCallLogger,
) : Logging {

    @Tool(description = "특정 파일의 전체 내용을 가져옵니다")
    fun getFileContent(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") path: String,
        @ToolParam(description = "브랜치명 또는 커밋 SHA") ref: String,
        toolContext: ToolContext,
    ): String = toolCallLogger.log("getFileContent", "repo=$repositoryFullName, path=$path, ref=$ref") {
        runBlocking(Dispatchers.IO) {
            val installationId = toolContext.installationId()
            logger.info("getFileContent 호출: repository={}, path={}, ref={}", repositoryFullName, path, ref)
            runCatching {
                val token = tokenPort.getInstallationToken(installationId)
                gitHubHttpClient.fetchFileContent(repositoryFullName, path, ref, token)
                    .let { Base64.getMimeDecoder().decode(it.content).toString(Charsets.UTF_8) }
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

    @Tool(description = "리뷰 중인 파일과 관련된 다른 파일을 조회합니다")
    fun getRelatedFile(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") filePath: String,
        @ToolParam(description = "브랜치명 또는 커밋 SHA") ref: String,
        toolContext: ToolContext,
    ): String = toolCallLogger.log("getRelatedFile", "repo=$repositoryFullName, filePath=$filePath, ref=$ref") {
        runBlocking(Dispatchers.IO) {
            val installationId = toolContext.installationId()
            logger.info("getRelatedFile 호출: repository={}, filePath={}, ref={}", repositoryFullName, filePath, ref)
            runCatching {
                val token = tokenPort.getInstallationToken(installationId)
                val sameDir = filePath.substringBeforeLast("/", missingDelimiterValue = "")
                val parentDir = sameDir.substringBeforeLast("/", missingDelimiterValue = "")
                val sameDirJob = async { gitHubHttpClient.fetchDirectoryContents(repositoryFullName, sameDir, ref, token) }
                val parentDirJob = if (sameDir != parentDir) {
                    async { gitHubHttpClient.fetchDirectoryContents(repositoryFullName, parentDir, ref, token) }
                } else null
                formatRelatedFiles(filePath, sameDir, sameDirJob.await(), parentDir, parentDirJob?.await())
            }.getOrElse { e ->
                when (e) {
                    is WebClientResponseException.NotFound ->
                        "디렉토리를 찾을 수 없습니다: ${filePath.substringBeforeLast("/", missingDelimiterValue = "(루트)")}"
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

    @Tool(description = "PR의 제목과 설명을 가져옵니다")
    fun getPRDescription(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "PR 번호") prNumber: Int,
        toolContext: ToolContext,
    ): String = toolCallLogger.log("getPRDescription", "repo=$repositoryFullName, prNumber=$prNumber") {
        runBlocking(Dispatchers.IO) {
            val installationId = toolContext.installationId()
            logger.info("getPRDescription 호출: repository={}, prNumber={}", repositoryFullName, prNumber)
            runCatching {
                val token = tokenPort.getInstallationToken(installationId)
                val response = gitHubHttpClient.fetchPrDescription(repositoryFullName, prNumber, token)
                val body = response.body?.takeIf { it.isNotBlank() } ?: "(설명 없음)"
                "제목: ${response.title}\n설명: $body"
            }.getOrElse { e ->
                when (e) {
                    is WebClientResponseException.NotFound ->
                        "PR을 찾을 수 없습니다: #$prNumber"
                    is WebClientResponseException ->
                        "GitHub API 오류 (${e.statusCode}): ${e.message}"
                    else -> {
                        logger.warn("getPRDescription 실패: repository={}, prNumber={}", repositoryFullName, prNumber, e)
                        "PR 설명을 가져오는 중 오류가 발생했습니다: ${e.message}"
                    }
                }
            }
        }
    }

    @Tool(description = "해당 파일의 최근 커밋 이력 최대 5건을 가져옵니다")
    fun getFileHistory(
        @ToolParam(description = "레포지토리 전체 이름 (예: octocat/my-repo)") repositoryFullName: String,
        @ToolParam(description = "파일 경로 (예: src/main/kotlin/Foo.kt)") filePath: String,
        toolContext: ToolContext,
    ): String = toolCallLogger.log("getFileHistory", "repo=$repositoryFullName, filePath=$filePath") {
        runBlocking(Dispatchers.IO) {
            val installationId = toolContext.installationId()
            logger.info("getFileHistory 호출: repository={}, filePath={}", repositoryFullName, filePath)
            runCatching {
                val token = tokenPort.getInstallationToken(installationId)
                val commits = gitHubHttpClient.fetchFileCommitHistory(repositoryFullName, filePath, token)
                if (commits.isEmpty()) return@runCatching "커밋 이력이 없습니다: $filePath"
                commits.mapIndexed { index, commit ->
                    val shortSha = commit.sha.take(7)
                    val message = commit.commit.message.lines().first()
                    val author = commit.commit.author.name
                    val date = commit.commit.author.date.take(10)
                    "[${index + 1}] $shortSha — $message\n    작성자: $author | $date"
                }.joinToString("\n")
            }.getOrElse { e ->
                when (e) {
                    is WebClientResponseException.NotFound ->
                        "파일을 찾을 수 없습니다: $filePath"
                    is WebClientResponseException ->
                        "GitHub API 오류 (${e.statusCode}): ${e.message}"
                    else -> {
                        logger.warn("getFileHistory 실패: repository={}, filePath={}", repositoryFullName, filePath, e)
                        "커밋 이력을 가져오는 중 오류가 발생했습니다: ${e.message}"
                    }
                }
            }
        }
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

    // ToolContext에서 installationId를 추출한다 — 누락 시 호출자 버그이므로 예외를 던진다
    private fun ToolContext.installationId(): Long =
        context["installationId"] as? Long ?: error("ToolContext에 installationId가 없습니다")
}
