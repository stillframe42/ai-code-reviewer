package stillframe42.aicodereviewer.rag.adapter.out.ai

import org.springframework.ai.document.Document
import org.springframework.core.io.ClassPathResource

// 마크다운 문서를 ## / ### 헤더 기준으로 분리하여 Document 리스트를 반환한다.
// 코드 블록(```) 내 ## 는 헤더로 인식하지 않는다.
// 헤더 이전 intro 내용 및 빈 섹션은 Document를 생성하지 않는다.
class MarkdownHeaderSplitter {


    // 모든 컨벤션 파일을 헤더 기준으로 분리하여 Document 리스트 반환
    fun prepare(): List<Document> =
        conventionFiles.flatMap { (path, category) ->
            val fileName = path.substringAfterLast("/")
            val text = ClassPathResource(path).inputStream.bufferedReader().readText()
            split(
                text,
                mapOf("source" to fileName, "category" to category, "version" to "1.0", "language" to "kotlin"),
            )
        }

    // 단일 텍스트를 ## / ### 헤더 기준으로 분리한다. (단위 테스트 가능하도록 internal 노출)
    internal fun split(text: String, baseMetadata: Map<String, Any>): List<Document> {
        val documents = mutableListOf<Document>()
        var currentH2: String? = null
        var currentH3: String? = null
        var inCodeBlock = false
        val buffer = mutableListOf<String>()

        // buffer 내용을 Document로 flush. 빈 buffer나 첫 헤더 이전 내용은 무시.
        fun flush() {
            val content = buffer.joinToString("\n").trim()
            buffer.clear()
            if (content.isBlank()) return
            // 로컬 변수로 캡처하여 스마트 캐스트 적용 (로컬 함수는 외부 var를 스마트 캐스트 불가)
            val h2 = currentH2
            val h3 = currentH3
            val (sectionHeader, depth) = when {
                h3 != null -> "${h2.orEmpty()} > $h3" to "h3"
                h2 != null -> h2 to "h2"
                else -> return // 첫 ## 헤더 이전 내용 무시
            }
            documents += Document(
                content,
                baseMetadata + mapOf("section_header" to sectionHeader, "depth" to depth),
            )
        }

        for (line in text.lines()) {
            // 코드 블록 진입/탈출 추적 (``` 으로 시작하는 줄)
            if (line.trimStart().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                buffer += line
                continue
            }
            if (inCodeBlock) {
                buffer += line
                continue
            }
            when {
                line.startsWith("## ") -> {
                    flush()
                    currentH2 = line.removePrefix("## ").trim()
                    currentH3 = null
                }
                line.startsWith("### ") -> {
                    flush()
                    currentH3 = line.removePrefix("### ").trim()
                }
                else -> buffer += line
            }
        }
        flush()
        return documents
    }
}
