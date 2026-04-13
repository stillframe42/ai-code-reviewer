package stillframe42.aicodereviewer.rag

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionKeywordSearchPort
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import java.io.File
import java.time.LocalDate

// 3가지 검색 방식(벡터 / 키워드 / 하이브리드) 품질 비교 실험
// LABELED 10개 + EDGE_CASE 5개 쿼리를 top-3 기준으로 gpt-4o-mini가 채점
// 실행 방법: ./gradlew hybridExperimentTest
@Tag("hybrid-experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HybridSearchExperimentTest {

    companion object {
        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer("pgvector/pgvector:pg16").also { it.start() }
        private val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            // GitHub API는 이 테스트에서 호출되지 않으므로 더미 주소 사용
            registry.add("github.api.base-url") { "http://localhost:9999" }

            val secretResource = ClassPathResource("application-secret.yml")
            if (!secretResource.exists()) return
            val props = YamlPropertiesFactoryBean().apply { setResources(secretResource) }.`object`
                ?: return
            props.getProperty("openai.api-key")?.takeIf { it.startsWith("sk-") }?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
        }

        private const val REPORT_PATH = "plans/202604-2w/hybrid-search-experiment.md"
    }

    @Autowired
    private lateinit var vectorPort: ConventionVectorPort

    @Autowired
    private lateinit var keywordPort: ConventionKeywordSearchPort

    @Autowired
    private lateinit var hybridService: HybridConventionSearchService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    @field:Qualifier("openAiChatClient")
    private lateinit var chatClient: ChatClient

    @BeforeAll
    fun checkApiKeyAndIndex() {
        val apiKey = environment.getProperty("openai.api-key").orEmpty()
        assumeTrue(apiKey.startsWith("sk-")) {
            "실제 OpenAI API 키(sk-*)가 없습니다. application-secret.yml을 확인하세요."
        }
        runBlocking { conventionIndexUseCase.reindex() }
    }

    @Test
    fun `하이브리드 검색 품질 비교 실험`() {
        // Task 4에서 구현
        TODO()
    }
}
