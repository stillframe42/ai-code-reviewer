package stillframe42.aicodereviewer.rag

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.adapter.`in`.cli.ConventionIndexingRunner
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

// ConventionIndexingRunner 통합 테스트
// AbstractIntegrationTest 상속 → Testcontainers PostgreSQL(pgvector) + WireMock 재사용
// app.rag.auto-index=false (integration-test 프로파일) → ApplicationReadyEvent 자동 인덱싱 비활성화
class ConventionIndexingRunnerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var runner: ConventionIndexingRunner

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @BeforeEach
    fun cleanAndStubEmbedding() {
        jdbcTemplate.execute("DELETE FROM vector_store")
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
    fun `--index-conventions 옵션이 있으면 문서가 인덱싱된다`() {
        runner.run(DefaultApplicationArguments("--index-conventions"))

        assertThat(countRows()).isGreaterThan(0)
    }

    @Test
    fun `옵션 없으면 인덱싱이 실행되지 않는다`() {
        runner.run(DefaultApplicationArguments())

        assertThat(countRows()).isEqualTo(0)
    }

    @Test
    fun `--force 옵션이 있으면 기존 데이터를 삭제하고 재인덱싱한다`() {
        // 1차 인덱싱
        runner.run(DefaultApplicationArguments("--index-conventions"))
        val countBefore = countRows()
        assertThat(countBefore).isGreaterThan(0)

        // --force 재인덱싱
        runner.run(DefaultApplicationArguments("--index-conventions", "--force"))
        val countAfter = countRows()

        assertThat(countAfter).isEqualTo(countBefore)
    }

    @Test
    fun `이미 인덱싱된 경우 --force 없이는 스킵된다`() {
        // 1차 인덱싱
        runBlocking { conventionIndexUseCase.index() }
        val countBefore = countRows()

        // --force 없이 실행 → 이미 있으므로 스킵
        runner.run(DefaultApplicationArguments("--index-conventions"))
        val countAfter = countRows()

        assertThat(countAfter).isEqualTo(countBefore)
    }

    private fun countRows(): Long =
        jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long::class.java) ?: 0L
}
