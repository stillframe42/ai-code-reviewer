package stillframe42.aicodereviewer.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
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

// 모든 통합 테스트의 베이스 클래스
// PostgreSQL Testcontainers + WireMockServer + Redis를 Singleton으로 관리한다.
// Spring Test 컨텍스트 캐싱과 함께 동작하여 전체 스위트에서 컨테이너/서버가 1번만 기동된다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
abstract class AbstractIntegrationTest {

    companion object {
        // Singleton — JVM당 컨테이너/서버 1개만 기동
        // also { it.start() }: 클래스 로드 시점에 즉시 기동 → @DynamicPropertySource 호출 전 준비 완료
        // 통합 테스트 컨텍스트 수가 늘어도 한계 도달이 어려워지도록 풀 자체를 작게 유지 (@DynamicPropertySource 의 hikari.maximum-pool-size=5) 가 1차 방어선.
        // max_connections=200 은 컨텍스트 다수일 때의 2차 방어선 — Spring 컨텍스트가 N개일 때 누적 연결이 한계를 못 넘도록.
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("pgvector/pgvector:pg16")
                .withCommand("postgres", "-c", "max_connections=200")
                .also { it.start() }

        val wireMock: WireMockServer =
            WireMockServer(
                options()
                    .dynamicPort()
                    // OpenAI 임베딩 배치 요청에서 input 수에 맞는 동적 응답 생성을 위한 커스텀 transformer 등록
                    .extensions(OpenAiEmbeddingBatchTransformer()),
            ).also { it.start() }

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
            // 컨텍스트 폭증 시에도 누적 연결이 작도록 풀 크기를 작게 — IT 의 DB 접근은 순차이고 5 connection 으로 충분
            registry.add("spring.datasource.hikari.maximum-pool-size") { "5" }
            registry.add("spring.datasource.hikari.minimum-idle") { "1" }
            // GitHub API → WireMock
            registry.add("github.api.base-url") { "http://localhost:${wireMock.port()}" }
            // Spring AI (Anthropic + OpenAI) → WireMock
            registry.add("spring.ai.anthropic.base-url") { "http://localhost:${wireMock.port()}" }
            // openai-java SDK 는 base-url 에 /v1 이 포함되는 규약 (경로에는 /embeddings 만 붙임) — 스텁 경로(/v1/*)와 정렬
            registry.add("spring.ai.openai.base-url") { "http://localhost:${wireMock.port()}/v1" }
            // Langfuse API → WireMock
            registry.add("langfuse.host") { "http://localhost:${wireMock.port()}" }
            // Remote Agent → WireMock
            registry.add("agent.remote.url") { "http://localhost:${wireMock.port()}" }
            // Redis → Testcontainers
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            // 통합 테스트는 짧은 polling 으로 IN_PROGRESS timeout 빠른 검증 — AgentPoller / AgentFallback IT 공통.
            // 운영 (60s/2s/30) 대비 짧지만 폴링 로직 자체는 동일하게 검증된다.
            registry.add("agent.remote.poll.max-attempts") { "30" }
            registry.add("agent.remote.poll.interval") { "10ms" }
            registry.add("agent.remote.poll.timeout") { "5s" }
        }
    }

    @LocalServerPort
    protected var port: Int = 0

    protected lateinit var client: RestTestClient

    @Autowired
    protected lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    @BeforeEach
    fun setUpBase() {
        // 테스트 간 stub 오염 방지 — 각 테스트는 깨끗한 WireMock 상태에서 시작
        wireMock.resetAll()
        // 테스트 간 Redis 캐시 오염 방지 — 이전 테스트에서 저장된 리뷰 캐시를 제거한다
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

// OpenAI /v1/embeddings 응답을 동적으로 생성하는 WireMock transformer
// Spring AI PgVectorStore는 여러 문서를 배치로 묶어 단일 API 요청에 전송하고,
// 응답의 data 배열에 input 배열과 동일한 수의 임베딩이 있어야 한다.
// WireMock 스텁에서 'openai-embedding-batch' transformer를 지정하면 자동으로 적용된다.
class OpenAiEmbeddingBatchTransformer : ResponseDefinitionTransformerV2 {

    private val objectMapper = ObjectMapper()

    // non-zero vector 1536차원 — zero vector는 pgvector cosine 검색에서 0건을 반환하므로 0.1f로 설정
    // 실제 임베딩 품질 검증이 아닌 저장 및 검색 흐름 확인 목적
    private val zeroVector = (1..1536).map { 0.1f }

    override fun getName(): String = "openai-embedding-batch"

    // transformer는 명시적으로 지정된 stub에만 적용 (global=false와 동일)
    override fun applyGlobally(): Boolean = false

    override fun transform(serveEvent: ServeEvent): ResponseDefinition {
        val requestBody = serveEvent.request.bodyAsString
        val inputCount = runCatching {
            val tree = objectMapper.readTree(requestBody)
            val inputNode = tree.get("input")
            if (inputNode != null && inputNode.isArray) inputNode.size() else 1
        }.getOrDefault(1)

        val embeddingArray = (0 until inputCount).joinToString(",") { index ->
            val vectorStr = zeroVector.joinToString(",")
            """{"object":"embedding","embedding":[$vectorStr],"index":$index}"""
        }

        val responseJson = """{"object":"list","data":[$embeddingArray],"model":"text-embedding-3-small","usage":{"prompt_tokens":$inputCount,"total_tokens":$inputCount}}"""

        return ResponseDefinitionBuilder.responseDefinition()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseJson)
            .build()
    }
}
