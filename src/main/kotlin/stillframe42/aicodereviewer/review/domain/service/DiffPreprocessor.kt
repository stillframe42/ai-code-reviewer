package stillframe42.aicodereviewer.review.domain.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.TokenEstimator
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.DiffPreprocessResult
import stillframe42.aicodereviewer.review.domain.model.FileReviewStrategy

// diff 전처리 도메인 서비스
// 불필요한 청크(바이너리, 테스트 파일 등)와 메타데이터 줄을 제거하여 AI에 전달할 토큰을 절감한다
@Component
class DiffPreprocessor {

    private val log = LoggerFactory.getLogger(DiffPreprocessor::class.java)

    fun preprocess(diff: String, options: DiffFilterOptions): DiffPreprocessResult {
        val tokensBefore = TokenEstimator.estimate(diff)

        // 1. diff --git 헤더 기준으로 파일별 청크 분리
        val chunks = splitIntoChunks(diff)

        val filteredFiles = mutableListOf<String>()

        val processedChunks = chunks.mapNotNull { chunk ->
            val fileName = extractFileName(chunk)

            // 2. 바이너리 파일 청크 제거
            if (isBinaryChunk(chunk)) {
                filteredFiles.add(fileName)
                return@mapNotNull null
            }

            // 3. 파일 패턴 필터 적용
            if (shouldExclude(fileName, options)) {
                filteredFiles.add(fileName)
                return@mapNotNull null
            }

            // 3.5. 확장자 전략 결정 — strategyOverrides glob 우선, 없으면 Classifier 결과 사용
            val strategy = resolveStrategy(fileName, options)
            if (strategy == FileReviewStrategy.Skip) {
                filteredFiles.add(fileName)
                return@mapNotNull null
            }

            // 4. 메타데이터 줄 제거 (diff --git, index, new/deleted/old/new file mode)
            var processed = removeMetadataLines(chunk)

            // 4.5. QUERY_REVIEW 청크에 리뷰 지침 헤더 주석 삽입
            if (strategy == FileReviewStrategy.QueryReview) {
                processed = insertQueryReviewHeader(processed, fileName)
            }

            // 5. contextLines < 3 이면 각 hunk에서 불필요한 context 줄 잘라냄
            if (options.contextLines < 3) {
                processed = trimContextLines(processed, options.contextLines)
            }

            processed.trim().ifBlank { null }
        }

        // 5.5. maxTokens 초과 시 변경량 기준 내림차순 정렬 후 한도 내 청크만 유지
        val finalChunks = if (options.maxTokens != null) {
            applyTokenLimit(processedChunks, options.maxTokens, filteredFiles)
        } else {
            processedChunks
        }

        // 6. 공백/빈줄 정리
        val resultDiff = finalChunks
            .joinToString("\n")
            .cleanWhitespace()

        val tokensAfter = TokenEstimator.estimate(resultDiff)

        // 7. INFO 로그 출력
        log.info(
            "전처리 완료 — 토큰: {} → {} ({}% 절감), 제외 파일: {}개",
            tokensBefore, tokensAfter,
            if (tokensBefore > 0) (tokensBefore - tokensAfter) * 100 / tokensBefore else 0,
            filteredFiles.size,
        )
        filteredFiles.forEach { log.info("  제외: {}", it) }

        return DiffPreprocessResult(
            diff = resultDiff,
            estimatedTokensBefore = tokensBefore,
            estimatedTokensAfter = tokensAfter,
            filteredFiles = filteredFiles,
        )
    }

    // diff --git 헤더를 기준으로 청크를 분리한다
    private fun splitIntoChunks(diff: String): List<String> {
        val chunks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null

        for (line in diff.lines()) {
            if (line.startsWith("diff --git ")) {
                current = mutableListOf()
                chunks.add(current)
            }
            current?.add(line)
        }

        return chunks.map { it.joinToString("\n") }
    }

    // diff --git a/... b/... 에서 b/ 이후 파일명 추출
    // removeMetadataLines 이후 처리된 청크에서는 --- a/filename 패턴으로 폴백
    private fun extractFileName(chunk: String): String {
        val firstLine = chunk.lines().firstOrNull { it.isNotBlank() } ?: return ""
        Regex("""^diff --git a/.+ b/(.+)$""").find(firstLine)?.groupValues?.get(1)?.let { return it }
        Regex("""^--- a/(.+)$""").find(firstLine)?.groupValues?.get(1)?.let { return it }
        return firstLine
    }

    // Binary files ... differ 문자열을 포함하면 바이너리 청크로 판단
    private fun isBinaryChunk(chunk: String): Boolean =
        chunk.contains("Binary files") && chunk.contains("differ")

    // strategyOverrides glob 패턴을 먼저 확인하고, 매칭되는 패턴이 없으면 Classifier 결과를 반환한다
    // 정책 우선순위: 명시적 glob 제외(shouldExclude) > strategyOverrides > FileExtensionClassifier
    private fun resolveStrategy(fileName: String, options: DiffFilterOptions): FileReviewStrategy {
        for ((pattern, strategy) in options.strategyOverrides) {
            if (matchesGlob(fileName, pattern)) return strategy
        }
        return FileExtensionClassifier.classify(fileName)
    }

    // QUERY_REVIEW 청크 맨 앞에 리뷰 지침 헤더 주석을 삽입한다
    // '#' 접두사로 diff 형식(---, +++, @@)과 구별하며 hunk 파싱에 영향을 주지 않는다
    private fun insertQueryReviewHeader(chunk: String, fileName: String): String {
        val header = "# [QUERY_REVIEW] $fileName: 변경 의도와 구조적 영향을 중심으로 리뷰하세요."
        return "$header\n$chunk"
    }

    // 제외 패턴에 매칭되는 파일이면 true 반환
    private fun shouldExclude(fileName: String, options: DiffFilterOptions): Boolean {
        val patterns = buildList {
            if (options.filterTestFiles) addAll(DiffFilterOptions.Defaults.TEST_FILE_PATTERNS)
            if (options.filterLockFiles) addAll(DiffFilterOptions.Defaults.LOCK_FILE_PATTERNS)
            addAll(DiffFilterOptions.Defaults.DEFAULT_EXCLUDE)
            addAll(options.additionalExcludePatterns)
        }
        return patterns.any { matchesGlob(fileName, it) }
    }

    // glob 패턴을 정규식으로 변환하여 파일 경로와 매칭한다
    // ** → 경로 구분자 포함 0개 이상, * → 경로 구분자 미포함 0개 이상
    private fun matchesGlob(path: String, pattern: String): Boolean {
        val sb = StringBuilder("^")
        var i = 0
        while (i < pattern.length) {
            when {
                i + 1 < pattern.length && pattern[i] == '*' && pattern[i + 1] == '*' -> {
                    sb.append(".*")
                    i += 2
                    // **/ → 0개 이상의 디렉토리 매칭 (슬래시 선택적)
                    if (i < pattern.length && pattern[i] == '/') {
                        sb.append("/?")
                        i++
                    }
                }
                pattern[i] == '*' -> { sb.append("[^/]*"); i++ }
                pattern[i] == '?' -> { sb.append("[^/]"); i++ }
                pattern[i] == '.' -> { sb.append("\\."); i++ }
                else -> { sb.append(Regex.escape(pattern[i].toString())); i++ }
            }
        }
        sb.append("$")
        return Regex(sb.toString()).matches(path)
    }

    // diff --git, index, new/deleted/old/new file mode 줄 제거
    // --- a/file, +++ b/file 줄은 파일 컨텍스트를 위해 유지
    private fun removeMetadataLines(chunk: String): String {
        val metadataPrefixes = listOf(
            "diff --git ", "index ",
            "new file mode", "deleted file mode", "old mode", "new mode",
        )
        return chunk.lines()
            .filter { line -> metadataPrefixes.none { line.startsWith(it) } }
            .joinToString("\n")
    }

    // contextLines 수에 맞게 각 hunk 내 context 줄을 트리밍한다
    // @@ hunk 헤더는 항상 유지하고, context 줄만 잘라낸다
    private fun trimContextLines(chunk: String, contextLines: Int): String {
        val lines = chunk.lines()
        val result = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("@@")) {
                // hunk 헤더 수집 후 hunk 본문 추출
                val hunkHeader = line
                i++
                val hunkBody = mutableListOf<String>()
                while (i < lines.size && !lines[i].startsWith("@@") &&
                    !lines[i].startsWith("---") && !lines[i].startsWith("+++")
                ) {
                    hunkBody.add(lines[i])
                    i++
                }
                result.add(hunkHeader)
                result.addAll(trimHunkContext(hunkBody, contextLines))
            } else {
                result.add(line)
                i++
            }
        }

        return result.joinToString("\n")
    }

    // hunk 본문에서 변경 줄(+/-) 기준 contextLines 범위 밖의 context 줄( 으로 시작)을 제거
    private fun trimHunkContext(hunkLines: List<String>, contextLines: Int): List<String> {
        if (contextLines == 0) {
            // context 줄 전부 제거, 변경 줄(+/-)만 유지
            return hunkLines.filter { !it.startsWith(" ") }
        }

        // 변경 줄 위치 파악
        val changedPositions = hunkLines.indices.filter { idx ->
            hunkLines[idx].startsWith("+") || hunkLines[idx].startsWith("-")
        }
        if (changedPositions.isEmpty()) return emptyList()

        // 각 변경 줄로부터 contextLines 범위 내 인덱스는 유지
        val keepIndices = mutableSetOf<Int>()
        for (pos in changedPositions) {
            for (j in (pos - contextLines).coerceAtLeast(0)..(pos + contextLines).coerceAtMost(hunkLines.lastIndex)) {
                keepIndices.add(j)
            }
        }

        return hunkLines.filterIndexed { idx, _ -> idx in keepIndices }
    }

    // 변경량 기준 내림차순 정렬 후 누적 토큰이 maxTokens 이하인 청크만 유지
    // 초과된 청크의 파일명은 filteredFiles에 추가
    private fun applyTokenLimit(
        chunks: List<String>,
        maxTokens: Int,
        filteredFiles: MutableList<String>,
    ): List<String> {
        val sorted = chunks.sortedByDescending { countChangedLines(it) }
        val kept = mutableListOf<String>()
        var accumulated = 0
        for (chunk in sorted) {
            val tokens = TokenEstimator.estimate(chunk)
            if (accumulated + tokens <= maxTokens) {
                kept.add(chunk)
                accumulated += tokens
            } else {
                filteredFiles.add(extractFileName(chunk))
            }
        }
        return kept
    }

    // +/- 로 시작하는 변경 줄 수를 반환한다 (헤더 줄 +++/--- 제외)
    private fun countChangedLines(chunk: String): Int =
        chunk.lines().count { line ->
            (line.startsWith("+") && !line.startsWith("+++")) ||
                (line.startsWith("-") && !line.startsWith("---"))
        }

    // trailing whitespace 제거 및 연속 빈줄(2개 이상) 압축
    private fun String.cleanWhitespace(): String =
        lines()
            .map { it.trimEnd() }
            .fold(mutableListOf<String>()) { acc, line ->
                if (line.isBlank() && acc.lastOrNull()?.isBlank() == true) acc
                else acc.also { it.add(line) }
            }
            .joinToString("\n")
            .trim()
}
