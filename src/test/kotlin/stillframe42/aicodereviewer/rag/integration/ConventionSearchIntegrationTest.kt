package stillframe42.aicodereviewer.rag.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

// ConventionVectorPort.search() 통합 테스트
// 기능 검증(assertion) + 검색 품질 육안 평가용 출력(assertion 없음)을 함께 제공한다.
// AbstractIntegrationTest 상속 → PostgreSQL(pgvector) Testcontainers + WireMock 재사용
// app.rag.auto-index=false (application-integration-test.yml) → 자동 인덱싱 비활성화
class ConventionSearchIntegrationTest : AbstractIntegrationTest(), Logging {

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    // AbstractIntegrationTest.setUpBase()(@BeforeEach) 실행 후 이 메서드가 실행된다.
    // → wireMock.resetAll() 이후 임베딩 스텁을 재등록하고, 테스트용 문서를 인덱싱한다.
    @BeforeEach
    fun stubAndIndex() {
        // OpenAI 임베딩 API 스텁 — 인덱싱 및 검색 쿼리 임베딩 요청 모두 처리
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("openai-embedding-batch")
                )
        )
        runBlocking { conventionIndexUseCase.reindex() }
    }

    @Test
    fun `기본 검색 시 topK 이하의 결과가 반환된다`() {
        // WireMock 환경에서는 임베딩이 랜덤 벡터로 생성되어 유사도가 낮으므로 threshold를 0.0으로 설정한다.
        val results = vectorPort.search("Kotlin null safety", topK = 5, similarityThreshold = 0.0)

        assertThat(results).isNotEmpty
        assertThat(results.size).isLessThanOrEqualTo(5)
    }

    @Test
    fun `category 필터를 지정하면 해당 카테고리 문서만 반환된다`() {
        val results = vectorPort.search("코드 작성 규칙", topK = 5, category = ConventionCategory.STYLE)

        assertThat(results).isNotEmpty
        results.forEach { doc ->
            assertThat(doc.metadata["category"])
                .`as`("반환된 문서의 category가 모두 STYLE이어야 한다")
                .isEqualTo("STYLE")
        }
    }

    @Test
    fun `quality_evaluation 10개 질문 검색 결과 육안 평가용 출력`() {
        // threshold 없음(0.0) — 가능한 많은 결과를 수집하여 품질을 육안으로 평가한다.
        // WireMock mock 환경에서는 모든 벡터가 동일하여 유사도 1.0으로 반환되므로
        // 실제 검색 품질 평가는 실제 OpenAI API 키를 사용하는 환경에서 수행해야 한다.
        ConventionTestQueries.LABELED.forEach { (label, query) ->
            val results = vectorPort.search(query, topK = 5, similarityThreshold = 0.0)
            logger.info("\n[{}] {}", label, query)
            if (results.isEmpty()) {
                logger.info("  결과 없음")
            } else {
                results.forEachIndexed { i, doc ->
                    val category = doc.metadata["category"]
                    val source = doc.metadata["source"]
                    val header = doc.metadata["section_header"]
                    logger.info("  {}. [{}] {} > \"{}\"", i + 1, category, source, header)
                }
            }
        }
    }
}
