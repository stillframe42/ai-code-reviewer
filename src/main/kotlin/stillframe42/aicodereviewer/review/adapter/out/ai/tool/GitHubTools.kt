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
import stillframe42.aicodereviewer.common.langfuse.LangfuseTraceContextHolder
import stillframe42.aicodereviewer.github.domain.model.RepositoryEntry
import stillframe42.aicodereviewer.github.domain.model.RepositoryEntryType
import stillframe42.aicodereviewer.github.domain.port.out.GitHubContentPort
import stillframe42.aicodereviewer.github.domain.port.out.GitHubRateLimitPort
import stillframe42.aicodereviewer.review.domain.port.out.ToolObservationPort
import java.util.concurrent.atomic.AtomicInteger

@Component
class GitHubTools(
    private val contentPort: GitHubContentPort,
    private val rateLimitPort: GitHubRateLimitPort,
    private val toolCallLogger: ToolCallLogger,
    private val toolObservationPort: ToolObservationPort,
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
    ) { installationId ->
        contentPort.getFileContent(repositoryFullName, path, ref, installationId)
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
    ) { installationId ->
        val sameDir = filePath.substringBeforeLast("/", missingDelimiterValue = "")
        val parentDir = sameDir.substringBeforeLast("/", missingDelimiterValue = "")
        val sameDirJob = async { contentPort.getDirectoryEntries(repositoryFullName, sameDir, ref, installationId) }
        val parentDirJob = if (sameDir != parentDir) {
            async { contentPort.getDirectoryEntries(repositoryFullName, parentDir, ref, installationId) }
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
    ) { installationId ->
        val description = contentPort.getPrDescription(repositoryFullName, prNumber, installationId)
        val body = description.body?.takeIf { it.isNotBlank() } ?: "(설명 없음)"
        "제목: ${description.title}\n설명: $body"
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
    ) { installationId ->
        val commits = contentPort.getFileCommitHistory(repositoryFullName, filePath, installationId)
        if (commits.isEmpty()) return@executeToolCall "커밋 이력이 없습니다: $filePath"
        commits.mapIndexed { index, commit ->
            val shortSha = commit.sha.take(7)
            val message = commit.message.lines().first()
            val date = commit.date.take(10)
            "[${index + 1}] $shortSha — $message\n    작성자: ${commit.authorName} | $date"
        }.joinToString("\n")
    }

    private fun formatRelatedFiles(
        filePath: String,
        sameDir: String,
        sameDirEntries: List<RepositoryEntry>,
        parentDir: String,
        parentDirEntries: List<RepositoryEntry>?,
    ): String = buildString {
        appendDirectorySection("[같은 디렉토리: ${sameDir.ifEmpty { "(루트)" }}]", sameDirEntries, filePath)
        parentDirEntries?.let {
            appendLine()
            appendDirectorySection("[상위 디렉토리: ${parentDir.ifEmpty { "(루트)" }}]", it, filePath)
        }
    }.trimEnd()

    private fun StringBuilder.appendDirectorySection(
        label: String,
        entries: List<RepositoryEntry>,
        excludePath: String,
    ) {
        appendLine(label)
        val files = entries.filter { it.type == RepositoryEntryType.FILE && it.path != excludePath }
        if (files.isEmpty()) appendLine("(파일 없음)")
        else files.forEach { appendLine("- ${it.path}") }
    }

    // 모든 Tool 메서드의 공통 골격 — Rate Limit 체크, 횟수 제한, Langfuse 컨텍스트 전파를 담당한다
    private fun executeToolCall(
        toolContext: ToolContext,
        toolName: String,
        argsLog: String,
        notFoundMessage: String,
        block: suspend CoroutineScope.(installationId: Long) -> String,
    ): String {
        val installationId = toolContext.installationId()
        rateLimitGuardMessage(installationId)?.let { return it }

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

        // runBlocking(Dispatchers.IO)은 새로운 스레드를 사용하므로 ThreadLocal이 전파되지 않는다.
        // 현재 코루틴 컨텍스트에서 traceId를 캡처하고 runBlocking 코루틴 컨텍스트로 명시적으로 전달한다.
        val currentTraceId = LangfuseTraceContextHolder.get()
        val traceContextElement = LangfuseTraceContextHolder.asElement(currentTraceId)
        return toolCallLogger.log(toolName, argsLog) {
            runBlocking(Dispatchers.IO + traceContextElement) {
                executeInSpan(toolName, argsLog, count, notFoundMessage, installationId, block)
            }
        }
    }

    // 잔여량이 임계값 미만이면 LLM에 반환할 안내 메시지를 생성한다 — 카운터 소모 없는 조기 반환용
    private fun rateLimitGuardMessage(installationId: Long): String? {
        val rateLimit = rateLimitPort.currentRateLimit(installationId) ?: return null
        return if (rateLimit.remaining < RATE_LIMIT_MIN_REMAINING) {
            "GitHub API Rate Limit 임박: ${rateLimit.remaining}건 남음, ${rateLimit.resetAt} 초기화 예정"
        } else null
    }

    // Span 생명주기 관리 + 실제 실행 + 에러 메시지 변환
    // CoroutineScope 수신자: getRelatedFile의 async { } 호출을 위해 block에 CoroutineScope를 전달한다
    private suspend fun CoroutineScope.executeInSpan(
        toolName: String,
        argsLog: String,
        count: Int,
        notFoundMessage: String,
        installationId: Long,
        block: suspend CoroutineScope.(installationId: Long) -> String,
    ): String {
        logger.info("{} 호출 ({}번째): {}", toolName, count, argsLog)
        val spanId = toolObservationPort.startSpan(toolName, mapOf("args" to argsLog, "count" to count))
        return runCatching {
            withTimeout(TOOL_CALL_TIMEOUT) {
                block(installationId)
            }
        }.onSuccess { result ->
            // Span은 추적용이므로 대용량 파일 내용 전송 방지를 위해 500자로 제한
            toolObservationPort.endSpan(spanId, result.take(500))
        }.onFailure { e ->
            toolObservationPort.endSpanWithError(spanId, e.message ?: e.javaClass.simpleName)
        }.getOrElse { e ->
            when (e) {
                is WebClientResponseException.NotFound -> notFoundMessage
                is WebClientResponseException -> "GitHub API 오류 (${e.statusCode}): ${e.message}"
                else -> {
                    logger.warn("{} 실패", toolName, e)
                    "[ERROR] $toolName 실패: ${e.message}"
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
        private const val RATE_LIMIT_MIN_REMAINING = 10
        private val TOOL_CALL_TIMEOUT = 10.seconds     // Tool 호출 당 타임아웃 (GitHub API hang 방지)
    }
}
