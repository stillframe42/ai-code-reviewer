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
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort

// ConventionKeywordSearchPort.search() 통합 테스트
// 키워드 기반 검색(벡터 검색이 아닌 풀텍스트/정확 매칭) 기능 검증
// AbstractIntegrationTest 상속 → PostgreSQL Testcontainers + WireMock 재사용
// app.rag.auto-index=false (application-integration-test.yml) → 자동 인덱싱 비활성화
class ConventionKeywordSearchIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var keywordSearchPort: ConventionKeywordSearchPort

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    // AbstractIntegrationTest.setUpBase()(@BeforeEach) 실행 후 이 메서드가 실행된다.
    // → wireMock.resetAll() 이후 임베딩 스텁을 재등록하고, 테스트용 문서를 인덱싱한다.
    @BeforeEach
    fun stubAndIndex() {
        jdbcTemplate.execute("DELETE FROM vector_store")
        // OpenAI 임베딩 API 스텁 — 인덱싱 시 임베딩 요청 처리
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
    fun `OWASP injection 키워드로 검색하면 관련 문서를 반환한다`() {
        val results = runBlocking { keywordSearchPort.search("OWASP injection", 5) }

        assertThat(results).isNotEmpty()
        assertThat(results.first().id).isNotNull()
        assertThat(results.first().text).containsIgnoringCase("owasp")
    }

    @Test
    fun `PreparedStatement SQL 키워드로 검색하면 관련 문서를 반환한다`() {
        val results = runBlocking { keywordSearchPort.search("PreparedStatement SQL", 5) }

        assertThat(results).isNotEmpty()
        assertThat(results.first().text).containsIgnoringCase("preparedstatement")
    }

    @Test
    fun `매칭되지 않는 키워드로 검색하면 빈 결과를 반환한다`() {
        val results = runBlocking { keywordSearchPort.search("xyzabcnotexisttoken", 5) }

        assertThat(results).isEmpty()
    }

    @Test
    fun `topK를 1로 지정하면 최대 1개 결과만 반환한다`() {
        val results = runBlocking { keywordSearchPort.search("OWASP injection", 1) }

        assertThat(results).hasSizeLessThanOrEqualTo(1)
    }
}
