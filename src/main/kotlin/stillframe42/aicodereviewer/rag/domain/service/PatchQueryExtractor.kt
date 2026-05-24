package stillframe42.aicodereviewer.rag.domain.service

// 전략 1: unified diff patch에서 추가된("+") 코드 라인의 첫 maxLines 줄을 추출해 검색 쿼리로 사용한다.
// 추가 라인이 없으면 파일명(path의 마지막 "/" 이후)으로 fallback. 둘 다 없으면 빈 문자열.
object PatchQueryExtractor {

    fun extract(patch: String, filePath: String?, maxLines: Int = 5): String {
        val addedLines = patch.lines()
            .filter { it.startsWith("+") && !it.startsWith("+++") }
            .map { it.removePrefix("+").trim() }
            .filter { it.isNotBlank() }
            .take(maxLines)

        if (addedLines.isNotEmpty()) return addedLines.joinToString("\n")
        return filePath?.substringAfterLast("/") ?: ""
    }
}
