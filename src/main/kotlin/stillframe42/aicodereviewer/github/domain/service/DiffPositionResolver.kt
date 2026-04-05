package stillframe42.aicodereviewer.github.domain.service

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.github.domain.model.PrReviewLineComment
import stillframe42.aicodereviewer.review.domain.model.CodeIssue

// diff 텍스트에서 (파일명, 새 파일 라인 번호) → diff position 인덱스를 구축하고
// CodeIssue 목록을 PrReviewLineComment로 변환하는 도메인 서비스
// GitHub PR Reviews API의 position은 파일별 첫 @@ 줄을 1로 시작하는 1-based 누적 카운터
@Component
class DiffPositionResolver : Logging {

    // raw diff와 CodeIssue 목록을 받아 인라인 코멘트 목록과 매핑 실패 이슈 목록을 반환
    fun resolve(rawDiff: String, issues: List<CodeIssue>): DiffPositionResolution {
        if (issues.isEmpty()) return DiffPositionResolution(emptyList(), emptyList())

        // 파일명 → (새 파일 라인 번호 → position) 인덱스 구축
        val positionIndex = buildPositionIndex(rawDiff)

        val lineComments = mutableListOf<PrReviewLineComment>()
        val unmappedIssues = mutableListOf<CodeIssue>()

        for (issue in issues) {
            val line = issue.line ?: run { unmappedIssues.add(issue); continue }

            val (resolvedFilename, position) = resolvePosition(positionIndex, issue.filename, line)

            if (resolvedFilename != null && position != null) {
                lineComments.add(PrReviewLineComment(resolvedFilename, position, formatBody(issue)))
                logger.debug("이슈 위치 매핑 성공: file={}, line={} → position={}", resolvedFilename, line, position)
            } else {
                unmappedIssues.add(issue)
                logger.debug("이슈 위치 매핑 실패: filename={}, line={}", issue.filename, line)
            }
        }

        logger.info(
            "diff position 매핑 완료 — 인라인 코멘트: {}개, 본문 포함: {}개",
            lineComments.size, unmappedIssues.size,
        )
        return DiffPositionResolution(lineComments, unmappedIssues)
    }

    // raw diff를 파싱하여 파일명 → (newLine → position) 인덱스를 반환
    // position은 파일별 첫 @@ 줄을 1로 시작하는 1-based 누적 카운터
    // --- a/file, +++ b/file 줄은 position 카운트에 포함하지 않는다
    internal fun buildPositionIndex(diff: String): Map<String, Map<Int, Int>> {
        val result = mutableMapOf<String, MutableMap<Int, Int>>()

        var currentFile: String? = null
        var position = 0
        var newLine = 0
        var inHunk = false  // @@ 줄 이후부터 position 카운트 시작

        for (line in diff.lines()) {
            when {
                // 새 파일 시작 — position 초기화
                line.startsWith("diff --git ") -> {
                    currentFile = null
                    position = 0
                    newLine = 0
                    inHunk = false
                }
                // +++ b/filename — 현재 파일명 추출 (--- 줄보다 뒤에 위치하므로 덮어씀)
                line.startsWith("+++ b/") -> {
                    currentFile = line.removePrefix("+++ b/")
                    result.getOrPut(currentFile) { mutableMapOf() }
                }
                // --- a/filename — 스킵 (position 카운트 대상 아님)
                line.startsWith("--- ") -> Unit
                // @@ hunk 헤더 — position 카운트 시작 (파일 내 첫 @@ = position 1)
                line.startsWith("@@") -> {
                    inHunk = true
                    position++
                    newLine = parseNewStart(line) ?: newLine
                }
                // hunk 내부 줄 처리 — val로 캡처해 스마트 캐스트 활성화 (var는 스마트 캐스트 불가)
                inHunk && currentFile != null -> {
                    val file = currentFile
                    when {
                        // context 줄 — 새 파일 라인 번호 포함, position 증가
                        line.startsWith(" ") -> {
                            position++
                            result.getValue(file)[newLine] = position
                            newLine++
                        }
                        // 추가 줄 — 새 파일 라인 번호 포함, position 증가
                        line.startsWith("+") -> {
                            position++
                            result.getValue(file)[newLine] = position
                            newLine++
                        }
                        // 삭제 줄 — 새 파일에 존재하지 않음, position만 증가
                        line.startsWith("-") -> {
                            position++
                        }
                    }
                }
            }
        }

        return result
    }

    // @@ -a,b +c,d @@ 헤더에서 새 파일의 시작 라인(c)을 파싱
    // +c,d 또는 +c (단일 라인 형식) 모두 처리
    internal fun parseNewStart(hunkHeader: String): Int? =
        Regex("""@@\s+-\d+(?:,\d+)?\s+\+(\d+)""").find(hunkHeader)
            ?.groupValues?.get(1)?.toIntOrNull()

    // CodeIssue의 filename과 line을 기반으로 파일명과 position을 결정
    // filename이 null이면 모든 파일 중 해당 line을 유일하게 가진 파일을 추론
    private fun resolvePosition(
        positionIndex: Map<String, Map<Int, Int>>,
        filename: String?,
        line: Int,
    ): Pair<String?, Int?> {
        if (filename != null) return filename to positionIndex[filename]?.get(line)
        // filename이 null — 유일한 파일을 추론 (멀티 파일에서 중복 시 매핑 포기)
        val candidates = positionIndex.entries.filter { (_, lineMap) -> lineMap.containsKey(line) }
        if (candidates.size != 1) return null to null
        val (resolvedFile, lineMap) = candidates.first()
        return resolvedFile to lineMap[line]
    }

    // 인라인 코멘트 본문 포맷
    private fun formatBody(issue: CodeIssue): String = buildString {
        appendLine("${issue.severity.emoji} **${issue.severity}** · ${issue.category}")
        appendLine()
        appendLine(issue.description)
        appendLine()
        append("💡 ${issue.suggestion}")
    }
}

// DiffPositionResolver 결과 — 인라인 코멘트와 위치 매핑 실패 이슈 목록
data class DiffPositionResolution(
    val lineComments: List<PrReviewLineComment>,
    val unmappedIssues: List<CodeIssue>,
)
