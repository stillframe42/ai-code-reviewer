package stillframe42.aicodereviewer.rag

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

// ConventionIndexer 통합 테스트
// AbstractIntegrationTest 상속 → Testcontainers PostgreSQL(pgvector) + WireMock 재사용
// app.rag.auto-index=false (application-integration-test.yml) → ApplicationReadyEvent 자동 인덱싱 비활성화
class ConventionIndexerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @BeforeEach
    fun cleanAndStubEmbedding() {
        // 테스트 간 데이터 오염 방지
        jdbcTemplate.execute("DELETE FROM vector_store")
        // OpenAI 임베딩 API 스텁 — OpenAiEmbeddingBatchTransformer가 input 배열 크기만큼 임베딩 반환
        // Spring AI PgVectorStore는 배치 단위로 임베딩을 요청하므로 input 배열 크기와 응답 data 배열 크기가 일치해야 함
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
    fun `빈 테이블에서 index() 호출 시 문서가 저장된다`() {
        runBlocking { conventionIndexUseCase.index() }

        val count = countRows()
        assertThat(count).isGreaterThan(0)
    }

    @Test
    fun `이미 데이터가 있으면 index()가 스킵된다`() {
        runBlocking { conventionIndexUseCase.index() }
        val countAfterFirst = countRows()

        runBlocking { conventionIndexUseCase.index() }
        val countAfterSecond = countRows()

        assertThat(countAfterSecond).isEqualTo(countAfterFirst)
    }

    @Test
    fun `reindex() 호출 시 기존 데이터를 삭제하고 재저장한다`() {
        runBlocking { conventionIndexUseCase.index() }
        val countAfterFirst = countRows()

        runBlocking { conventionIndexUseCase.reindex() }
        val countAfterReindex = countRows()

        assertThat(countAfterReindex).isEqualTo(countAfterFirst)
    }

    @Test
    fun `저장된 문서에 category와 source 메타데이터가 포함된다`() {
        runBlocking { conventionIndexUseCase.index() }

        // metadata JSONB 컬럼에서 category, source 키 존재 확인
        val metadataList = jdbcTemplate.queryForList(
            "SELECT metadata::text FROM vector_store LIMIT 10"
        )
        assertThat(metadataList).isNotEmpty
        val firstMetadata = metadataList.first().values.first().toString()
        assertThat(firstMetadata).contains("category")
        assertThat(firstMetadata).contains("source")
    }

    @Test
    fun `POST reindex 엔드포인트 호출 시 200을 반환한다`() {
        runBlocking { conventionIndexUseCase.index() }

        client.post()
            .uri("/internal/conventions/reindex")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `search() 호출 시 topK 이하의 결과가 반환된다`() {
        // given: 인덱싱 (BeforeEach에서 WireMock 스텁 이미 설정됨)
        runBlocking { conventionIndexUseCase.index() }

        // when
        val results = vectorPort.search("Kotlin null safety", topK = 3)

        // then
        assertThat(results.size).isGreaterThan(0)
        assertThat(results.size).isLessThanOrEqualTo(3)
    }

    @Test
    fun `임베딩 API 429 응답 시 재시도하여 성공한다`() {
        // 첫 번째 임베딩 요청: 429 Rate Limit
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .inScenario("rate-limit-retry")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(
                    aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"message":"Rate limit exceeded","type":"requests","code":"rate_limit_exceeded"}}""")
                )
                .willSetStateTo("retry")
        )
        // 두 번째 임베딩 요청: 200 성공
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .inScenario("rate-limit-retry")
                .whenScenarioStateIs("retry")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("openai-embedding-batch")
                )
        )

        runBlocking { conventionIndexUseCase.index() }

        assertThat(countRows()).isGreaterThan(0)
    }

    private fun countRows(): Long =
        jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long::class.java) ?: 0L
}
