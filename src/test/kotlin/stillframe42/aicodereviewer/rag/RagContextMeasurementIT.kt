package stillframe42.aicodereviewer.rag

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// RAG 컨텍스트 베이스라인 측정 — 컨텍스트 압축(tasks_20260416.md Phase 2~5) 전 baseline 수집
// 일반 빌드에서는 자동 스킵. 수동 실행:
//   RAG_MANUAL_TEST=true ./gradlew test --tests "*RagContextMeasurementIT*"
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "RAG_MANUAL_TEST", matches = "true")
class RagContextMeasurementIT {

    companion object {
        // AbstractIntegrationTest의 Singleton 컨테이너 재사용 — 새 컨테이너 기동 없이 기존 인스턴스 공유
        val wireMock = AbstractIntegrationTest.wireMock
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("github.api.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("langfuse.host") { "http://localhost:${wireMock.port()}" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            // spring.ai.openai.base-url 미설정 → application-ai.yml 기본값(실제 OpenAI API) 사용
            // 이유: WireMock mock 임베딩은 모든 벡터가 동일하여 HNSW 검색이 0건 반환
            val secrets = readSecrets()
            secrets["openai"]?.let { key ->
                registry.add("openai.api-key") { key }
                registry.add("spring.ai.openai.api-key") { key }
            }
        }

        // application-secret.yml에서 OpenAI API 키를 읽어 반환
        // (Anthropic 키는 본 측정에서 불필요 — 임베딩만 사용)
        private fun readSecrets(): Map<String, String> = runCatching {
            val file = java.io.File("src/main/resources/application-secret.yml")
            check(file.exists()) { "application-secret.yml 파일을 찾을 수 없습니다" }
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(file.inputStream())
            buildMap {
                (map["openai"] as? Map<*, *>)?.get("api-key")?.let { put("openai", it as String) }
            }
        }.getOrElse { e ->
            System.err.println("[RagContextMeasurementIT] application-secret.yml 로드 실패: ${e.message}")
            emptyMap()
        }
    }

    @Test
    fun `스켈레톤 컴파일 검증 placeholder`() = runBlocking {
        // Task 5에서 실제 측정 로직으로 교체됨
        Unit
    }
}
