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

// ConventionIndexer 통합 테스트
// AbstractIntegrationTest 상속 → Testcontainers PostgreSQL(pgvector) + WireMock 재사용
// app.rag.auto-index=false (application-integration-test.yml) → ApplicationReadyEvent 자동 인덱싱 비활성화
class ConventionIndexerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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

    private fun countRows(): Long =
        jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long::class.java) ?: 0L
}
