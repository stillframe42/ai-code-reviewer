package stillframe42.aicodereviewer.rag

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.document.Document
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.yaml.snakeyaml.Yaml
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.SECURITY
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.STYLE

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

        // 측정 대상 쿼리 — 카테고리당 2개씩, FileCategoryMapper와 정확히 일치하도록 선정
        // 카테고리 매핑 우선순위: SECURITY > API > ARCH > STYLE
        val SAMPLE_QUERIES: List<SampleQuery> = listOf(
            // ARCH (헥사고날 아키텍처, 트랜잭션, N+1)
            SampleQuery("arch-1", "OrderService.kt",
                "src/main/kotlin/stillframe42/codereviewertester/order/application/OrderService.kt", ARCH),
            SampleQuery("arch-2", "ReviewRequestRepository.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/review/adapter/out/persistence/ReviewRequestRepository.kt", ARCH),

            // API (Controller, REST)
            SampleQuery("api-1", "OrderController.kt",
                "src/main/kotlin/stillframe42/codereviewertester/order/adapter/web/OrderController.kt", API),
            SampleQuery("api-2", "ChatController.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/chat/adapter/in/web/ChatController.kt", API),

            // STYLE (Kotlin 컨벤션) — 키워드 미매칭 파일명
            SampleQuery("style-1", "DiffPreprocessor.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/review/domain/service/DiffPreprocessor.kt", STYLE),
            SampleQuery("style-2", "ReviewMode.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/review/domain/model/ReviewMode.kt", STYLE),

            // SECURITY (인증, JWT)
            SampleQuery("sec-1", "JwtAuthenticationFilter.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/security/JwtAuthenticationFilter.kt", SECURITY),
            SampleQuery("sec-2", "SecurityConfig.kt",
                "src/main/kotlin/stillframe42/aicodereviewer/config/SecurityConfig.kt", SECURITY),
        )

        private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator()

        // 텍스트의 토큰 수를 반환 (cl100k_base 기준 — Spring AI 기본 인코딩)
        internal fun countTokens(text: String): Int = tokenEstimator.estimate(text)
    }

    // 측정 대상 단일 쿼리 (카테고리, 파일 경로, 검색 텍스트)
    data class SampleQuery(
        val id: String,
        val queryText: String,
        val filePath: String,
        val expectedCategory: ConventionCategory,
    )

    // 청크 단위 측정 결과
    data class ChunkMeasurement(
        val rank: Int,
        val tokens: Int,
        val chars: Int,
        val sourceFile: String?,
        val text: String,
    )

    // 쿼리별 종합 측정 결과
    data class QueryMeasurement(
        val query: SampleQuery,
        val chunks: List<ChunkMeasurement>,
        val totalTokens: Int,    // 청크 토큰 합계 (구분자 제외)
        val joinedTokens: Int,   // join("\n\n---\n\n") 후 실제 프롬프트 주입 형태 토큰 수
    )

    @Test
    fun `스켈레톤 컴파일 검증 placeholder`() = runBlocking {
        // Task 5에서 실제 측정 로직으로 교체됨
        Unit
    }
}
