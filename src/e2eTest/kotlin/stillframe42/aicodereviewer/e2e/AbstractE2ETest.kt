package stillframe42.aicodereviewer.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.e2e.support.ContainerLogTail
import stillframe42.aicodereviewer.e2e.support.OpenAiEmbeddingBatchTransformer
import stillframe42.aicodereviewer.e2e.support.RemoteAgentContainer

// E2E 베이스 — AbstractIntegrationTest 와 의도적 격리.
// 핵심 차이:
//   1. agent.remote.url 이 WireMock 이 아닌 실제 Remote 컨테이너 mapped port
//   2. agent.remote.poll.interval 이 10ms 가 아닌 500ms — 폴링 박제 의미 유지
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
abstract class AbstractE2ETest {

    companion object {
        // 기동 순서 강제: WireMock → Postgres/Redis → Remote 컨테이너
        // Remote 컨테이너의 OPENAI_BASE_URL env 가 wireMock.port() 에 의존하므로 WireMock 이 반드시 먼저.
        val wireMock: WireMockServer =
            WireMockServer(
                options()
                    .dynamicPort()
                    .extensions(OpenAiEmbeddingBatchTransformer()),
            ).also { it.start() }

        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("pgvector/pgvector:pg16")
                .withCommand("postgres", "-c", "max_connections=200")
                .also { it.start() }

        val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)
                .also { it.start() }

        val remoteAgentLogs: ContainerLogTail = ContainerLogTail()

        val remoteAgent: GenericContainer<*> =
            RemoteAgentContainer.create(wireMockHostPort = wireMock.port(), logTail = remoteAgentLogs)
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.hikari.maximum-pool-size") { "5" }
            registry.add("spring.datasource.hikari.minimum-idle") { "1" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }

            // 외부 호출 → WireMock
            registry.add("github.api.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("spring.ai.anthropic.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("spring.ai.openai.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("langfuse.host") { "http://localhost:${wireMock.port()}" }

            // Remote 에이전트만 실제 컨테이너
            registry.add("agent.remote.url") { "http://localhost:${remoteAgent.getMappedPort(8081)}" }
            // IN_PROGRESS 가 최소 1회 박히도록 운영에 가까운 interval
            registry.add("agent.remote.poll.interval") { "500ms" }
            registry.add("agent.remote.poll.timeout") { "30s" }
            registry.add("agent.remote.poll.max-attempts") { "60" }
        }
    }

    @LocalServerPort
    protected var port: Int = 0

    protected lateinit var client: RestTestClient

    @Autowired
    protected lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    @BeforeEach
    fun setUpBase() {
        wireMock.resetAll()
        redisTemplate.connectionFactory
            .reactiveConnection
            .serverCommands()
            .flushAll()
            .block()
        client = RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }
}
