package stillframe42.aicodereviewer.evaluation.integration

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.document.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationMetric
import stillframe42.aicodereviewer.evaluation.domain.port.out.RagEvaluationPort
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// 실제 OpenAI API 호출 통합 테스트 — 비용 발생하므로 수동 실행만 허용
// EVAL_MANUAL_TEST=true ./gradlew test --tests "*EvaluationAdapterIT*"
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariable(named = "EVAL_MANUAL_TEST", matches = "true")
class EvaluationAdapterIT {

    companion object {
        val postgres: PostgreSQLContainer = AbstractIntegrationTest.postgres
        val redis: GenericContainer<*> = AbstractIntegrationTest.redis

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    private lateinit var ragEvaluationPort: RagEvaluationPort

    @Test
    fun `Faithfulness 평가 - 컨텍스트에 근거한 리뷰는 높은 점수를 받는다`() = runBlocking {
        val context = listOf(
            Document.builder()
                .text("SQL Injection 방어: 모든 DB 쿼리에 파라미터 바인딩 사용. 문자열 직접 조합 금지.")
                .build(),
        )
        val review = "이 코드는 SQL 쿼리에 문자열 연결을 사용하고 있어 SQL Injection 취약점이 있습니다. 파라미터 바인딩을 사용해야 합니다."

        val score = ragEvaluationPort.evaluateFaithfulness(context, review)

        assertThat(score.metric).isEqualTo(EvaluationMetric.FAITHFULNESS)
        assertThat(score.score).isBetween(0.0, 1.0)
        assertThat(score.reason).isNotBlank()
    }

    @Test
    fun `Context Precision 평가 - 관련 문서 비율을 반환한다`() = runBlocking {
        val docs = listOf(
            Document.builder().id("doc-1").text("SQL Injection 방어 규칙").build(),
            Document.builder().id("doc-2").text("JPA Entity는 data class 금지").build(),
        )

        val score = ragEvaluationPort.evaluateContextPrecision(
            query = "SecurityAuditRepository.kt",
            retrievedDocs = docs,
            relevantConvention = "security-checklist.md#A03",
        )

        assertThat(score.metric).isEqualTo(EvaluationMetric.CONTEXT_PRECISION)
        assertThat(score.score).isBetween(0.0, 1.0)
        assertThat(score.reason).isNotBlank()
    }

    @Test
    fun `Context Recall 평가 - 기대 이슈 커버 비율을 반환한다`() = runBlocking {
        val expectedIssues = listOf("SQL Injection 취약점 — 파라미터 바인딩 미사용")
        val docs = listOf(
            Document.builder().id("doc-1").text("SQL Injection 방어: 모든 DB 쿼리에 파라미터 바인딩 사용.").build(),
        )

        val score = ragEvaluationPort.evaluateContextRecall(expectedIssues, docs)

        assertThat(score.metric).isEqualTo(EvaluationMetric.CONTEXT_RECALL)
        assertThat(score.score).isBetween(0.0, 1.0)
        assertThat(score.reason).isNotBlank()
    }
}
