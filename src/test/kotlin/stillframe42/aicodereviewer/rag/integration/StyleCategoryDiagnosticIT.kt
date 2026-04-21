package stillframe42.aicodereviewer.rag.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

// STYLE 카테고리 검색이 5/5 실패하는 원인을 진단한다 (Step A-3).
// SQL로 청크 분포·내용을 확인하고, 실제 검색 결과에서 매칭되는 청크와 유사도를 출력.
// 결과는 stdout에 표시되며 사람이 분석하여 A-4(styleFilterBypass) 적용 여부를 결정한다.
//
// 제한 사항: AbstractIntegrationTest가 OpenAI /v1/embeddings를 WireMock으로 stub하고
// 모든 임베딩을 동일한 0.1f 벡터로 반환하므로, part (3) 벡터 검색의 "의미적 유사도"는
// 실제 환경과 다르다. Part (1)·(2) 메타데이터·청크 분포는 실제 인덱싱 결과이므로 의미 있음.
class StyleCategoryDiagnosticIT : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @BeforeEach
    fun stubEmbedding() {
        // OpenAI 임베딩 API 스텁 — OpenAiEmbeddingBatchTransformer가 input 배열 크기만큼 임베딩 반환
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("openai-embedding-batch")
                )
        )
    }

    @Test
    fun `STYLE 카테고리 검색 진단`(): Unit = runBlocking {
        // 인덱싱 보장
        conventionIndexUseCase.reindex()

        println("\n${"=".repeat(80)}")
        println("STYLE 카테고리 검색 진단 (Step A-3)")
        println("=".repeat(80))

        // (1) 카테고리별 청크 분포
        println("\n[1] 카테고리별 청크 분포")
        val categoryCounts = jdbcTemplate.queryForList(
            "SELECT metadata->>'category' as category, count(*) as cnt " +
                "FROM vector_store GROUP BY 1 ORDER BY cnt DESC"
        )
        categoryCounts.forEach { row ->
            println("  ${row["category"]}: ${row["cnt"]}개")
        }

        // (2) STYLE 청크 샘플 (최대 20개)
        println("\n[2] STYLE 청크 샘플")
        val styleChunks = jdbcTemplate.queryForList(
            "SELECT metadata->>'section_header' as section, " +
                "       substr(content, 1, 100) as preview " +
                "FROM vector_store WHERE metadata->>'category' = 'STYLE' LIMIT 20"
        )
        if (styleChunks.isEmpty()) {
            println("  ⚠️  STYLE 청크가 0개 — 인덱싱 누락. kotlin-style.md 헤더 구조 점검 필요")
        } else {
            styleChunks.forEachIndexed { i, row ->
                println("  [${i + 1}] section=${row["section"]}")
                println("      preview=${row["preview"]}")
            }
        }

        // (3) 5개 STYLE 케이스 파일명으로 vector search 실행 — 매칭 청크 확인
        // (WireMock 임베딩 stub로 인해 유사도는 실제와 다름 — 매칭 청크 개수/메타데이터만 참고)
        println("\n[3] STYLE 케이스 파일명별 vector search 결과 (topK=5, category=STYLE, threshold=0.0)")
        println("    ⚠️ WireMock stub: 임베딩이 모두 동일하므로 유사도는 실제 환경과 다름")
        val styleCaseFileNames = listOf(
            "ReportFormatter.kt",
            "UserMapper.kt",
            "DateUtils.kt",
            "ConfigParser.kt",
            "MathHelper.kt",
        )
        styleCaseFileNames.forEach { fileName ->
            val results = vectorPort.search(fileName, 5, ConventionCategory.STYLE, 0.0)
            println("\n  쿼리: $fileName → ${results.size}개 청크")
            results.forEachIndexed { i, doc ->
                val section = doc.metadata["section_header"] ?: "?"
                val score = doc.metadata["distance"] ?: doc.metadata["score"] ?: "?"
                val preview = (doc.text ?: "").take(80).replace("\n", " ")
                println("    [${i + 1}] section=$section score=$score preview=$preview")
            }
        }

        println("\n${"=".repeat(80)}")
        println("진단 완료 — 위 결과를 보고 styleFilterBypass 효과 가설 결정 (spec §2 A-3 분기표 참조)")
        println("=".repeat(80))
    }
}
