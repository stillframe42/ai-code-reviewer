package stillframe42.aicodereviewer.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer

// 모든 통합 테스트의 베이스 클래스
// PostgreSQL Testcontainers + WireMockServer + Redis를 Singleton으로 관리한다.
// Spring Test 컨텍스트 캐싱과 함께 동작하여 전체 스위트에서 컨테이너/서버가 1번만 기동된다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
abstract class AbstractIntegrationTest {

    companion object {
        // Singleton — JVM당 컨테이너/서버 1개만 기동
        // also { it.start() }: 클래스 로드 시점에 즉시 기동 → @DynamicPropertySource 호출 전 준비 완료
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:16-alpine").also { it.start() }

        val wireMock: WireMockServer =
            WireMockServer(options().dynamicPort()).also { it.start() }

        val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            // PostgreSQL → Testcontainers
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            // GitHub API → WireMock
            registry.add("github.api.base-url") { "http://localhost:${wireMock.port()}" }
            // Spring AI (Anthropic + OpenAI) → WireMock
            registry.add("spring.ai.anthropic.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("spring.ai.openai.base-url") { "http://localhost:${wireMock.port()}" }
            // Langfuse API → WireMock
            registry.add("langfuse.host") { "http://localhost:${wireMock.port()}" }
            // Redis → Testcontainers
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
        }
    }

    @LocalServerPort
    protected var port: Int = 0

    protected lateinit var client: RestTestClient

    @BeforeEach
    fun setUpBase() {
        // 테스트 간 stub 오염 방지 — 각 테스트는 깨끗한 WireMock 상태에서 시작
        wireMock.resetAll()
        client = RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }
}
