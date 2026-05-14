package stillframe42.aicodereviewer.agent.integration

import java.io.File
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrFileStatus

data class Sample(
    val id: String,
    val patchPath: String,
    val prNumber: Int,
)

object PayloadSamples {

    val SAMPLES: List<Sample> = listOf(
        Sample("security-sql", "src/test/resources/fixtures/review/security-sql-injection.patch", 1001),
        Sample("arch-jpa", "src/test/resources/fixtures/review/arch-jpa-entity-dataclass.patch", 1002),
        Sample("style-long", "src/test/resources/fixtures/review/style-long-function.patch", 1003),
    )

    fun readPatch(sample: Sample): String = File(sample.patchPath).readText()

    // patch 의 `+++ b/<path>` 헤더에서 변경 파일 목록을 추출한다
    // baseline 측정 목적이라 status 는 ADDED 고정 (fixture 셋 모두 'new file mode' 임을 사전 확인)
    // additions/deletions/changes 는 패칭 자체에 의미가 없으므로 0 — payload 직렬화에 영향 없음
    fun parsePrFiles(patch: String): List<PrFile> =
        PLUS_HEADER_REGEX.findAll(patch)
            .map { match ->
                PrFile(
                    filename = match.groupValues[1],
                    status = PrFileStatus.ADDED,
                    additions = 0,
                    deletions = 0,
                    changes = 0,
                    patch = null,
                    previousFilename = null,
                )
            }
            .toList()

    private val PLUS_HEADER_REGEX = Regex("""^\+\+\+ b/(.+)$""", RegexOption.MULTILINE)
}
