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
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.rag.application.ConventionContextService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

class ConventionContextServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var conventionContextService: ConventionContextService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun stubAndIndex() {
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
        // LlmContextCompressorAdapter가 호출하는 OpenAI Chat API 스텁 (압축 정상 경로)
        WireMockStubs.stubOpenAiChatResponse(wireMock, body = "compressed test content")
        runBlocking { conventionIndexUseCase.reindex() }
    }

    @Test
    fun `SECURITY 파일 경로로 buildContext 호출 시 비어있지 않은 컨텍스트를 반환한다`() {
        val context = runBlocking {
            conventionContextService.buildContext(
                query = "SecurityConfig.kt",
                filePath = "src/main/kotlin/stillframe42/SecurityConfig.kt",
            )
        }
        assertThat(context).isNotBlank()
    }

    @Test
    fun `빈 DB에서 검색하면 빈 문자열을 반환한다`() {
        // reindex() 없이 빈 DB 상태로 검색 — 결과 없음 → 빈 문자열 반환
        jdbcTemplate.execute("DELETE FROM vector_store")
        val context = runBlocking {
            conventionContextService.buildContext(
                query = "xyznotexisttoken99999",
            )
        }
        assertThat(context).isEmpty()
    }

    @Test
    fun `filePath가 null이면 카테고리 없이 전체 검색하며 예외가 발생하지 않는다`() {
        val context = runBlocking {
            conventionContextService.buildContext(query = "OWASP injection")
        }
        // 전체 검색이므로 결과 존재 가능 — 예외 없이 String을 반환하는지만 검증
        assertThat(context).isNotNull()
    }

    @Test
    fun `반환된 컨텍스트는 구분자(---)로 문서를 구분한다`() {
        val context = runBlocking {
            conventionContextService.buildContext(
                query = "SecurityConfig.kt",
                filePath = "src/main/kotlin/stillframe42/SecurityConfig.kt",
            )
        }
        // 구분자로 분리된 각 파트는 빈 문자열이 아니어야 한다
        val parts = context.split("\n\n---\n\n")
        assertThat(parts).allSatisfy { part ->
            assertThat(part).isNotBlank()
        }
    }
}
