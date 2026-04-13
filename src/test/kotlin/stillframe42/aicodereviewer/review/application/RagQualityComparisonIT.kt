package stillframe42.aicodereviewer.review.application

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.rag.application.ConventionContextService
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// RAG 적용 전/후 리뷰 품질 비교용 수동 실행 테스트
// AbstractIntegrationTest를 상속하지 않음 — Anthropic API를 WireMock으로 리다이렉트하지 않기 위해
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Disabled("수동 실행용 — 실제 Anthropic API 키 필요")
class RagQualityComparisonIT {

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
            // spring.ai.anthropic.base-url 미설정 → application-ai.yml 기본값(실제 API) 사용
            registry.add("spring.ai.openai.base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("langfuse.host") { "http://localhost:${wireMock.port()}" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
        }

        // PR #11 diff: stillframe42/code-reviewer-tester
        // 의도적 컨벤션 위반:
        //   1. OrderController가 OrderUseCase 인터페이스 대신 OrderService 구현체에 직접 의존
        //   2. 클래스명 OrderService (헥사고날 컨벤션상 DefaultOrderService 여야 함)
        //   3. OrderUseCase 포트 인터페이스 부재
        private val DIFF = """
diff --git a/src/main/kotlin/stillframe42/codereviewertester/order/adapter/web/OrderController.kt b/src/main/kotlin/stillframe42/codereviewertester/order/adapter/web/OrderController.kt
new file mode 100644
index 0000000..7964ccd
--- /dev/null
+++ b/src/main/kotlin/stillframe42/codereviewertester/order/adapter/web/OrderController.kt
@@ -0,0 +1,52 @@
+package stillframe42.codereviewertester.order.adapter.web
+
+import org.springframework.http.ResponseEntity
+import org.springframework.web.bind.annotation.GetMapping
+import org.springframework.web.bind.annotation.PathVariable
+import org.springframework.web.bind.annotation.PostMapping
+import org.springframework.web.bind.annotation.RequestMapping
+import org.springframework.web.bind.annotation.RequestParam
+import org.springframework.web.bind.annotation.RestController
+import stillframe42.codereviewertester.order.application.OrderService
+import stillframe42.codereviewertester.order.domain.Order
+
+@RestController
+@RequestMapping("/orders")
+class OrderController(private val orderService: OrderService) {
+
+    @GetMapping("/{orderId}")
+    fun getOrder(@PathVariable orderId: String): ResponseEntity<Order> {
+        val order = orderService.getOrder(orderId)
+        return ResponseEntity.ok(order)
+    }
+
+    @GetMapping
+    fun getOrdersByCustomer(@RequestParam customerId: String): ResponseEntity<List<Order>> {
+        val orders = orderService.getOrdersByCustomer(customerId)
+        return ResponseEntity.ok(orders)
+    }
+
+    @PostMapping("/{orderId}/confirm")
+    fun confirmOrder(@PathVariable orderId: String): ResponseEntity<Order> {
+        val confirmed = orderService.confirmOrder(orderId)
+        return ResponseEntity.ok(confirmed)
+    }
+
+    @PostMapping("/{orderId}/cancel")
+    fun cancelOrder(@PathVariable orderId: String): ResponseEntity<Order> {
+        val cancelled = orderService.cancelOrder(orderId)
+        return ResponseEntity.ok(cancelled)
+    }
+
+    @PostMapping("/{orderId}/refund")
+    fun refundOrder(@PathVariable orderId: String): ResponseEntity<Order> {
+        val refunded = orderService.refundOrder(orderId)
+        return ResponseEntity.ok(refunded)
+    }
+
+    @GetMapping("/pending")
+    fun getPendingOrders(): ResponseEntity<List<Order>> {
+        val pending = orderService.getPendingOrders()
+        return ResponseEntity.ok(pending)
+    }
+}
diff --git a/src/main/kotlin/stillframe42/codereviewertester/order/application/OrderService.kt b/src/main/kotlin/stillframe42/codereviewertester/order/application/OrderService.kt
new file mode 100644
index 0000000..a50932f
--- /dev/null
+++ b/src/main/kotlin/stillframe42/codereviewertester/order/application/OrderService.kt
@@ -0,0 +1,54 @@
+package stillframe42.codereviewertester.order.application
+
+import org.springframework.stereotype.Service
+import stillframe42.codereviewertester.order.domain.Order
+import stillframe42.codereviewertester.order.domain.OrderRepository
+import stillframe42.codereviewertester.order.domain.OrderStatus
+
+@Service
+class OrderService(private val orderRepository: OrderRepository) {
+
+    fun getOrder(orderId: String): Order {
+        return orderRepository.findById(orderId)
+            ?: throw IllegalArgumentException("주문을 찾을 수 없습니다: ${'$'}orderId")
+    }
+
+    fun getOrdersByCustomer(customerId: String): List<Order> {
+        return orderRepository.findAllByCustomerId(customerId)
+    }
+
+    fun cancelOrder(orderId: String): Order {
+        val order = getOrder(orderId)
+
+        if (!order.canBeCancelled()) {
+            throw IllegalStateException("현재 상태에서는 취소할 수 없습니다: ${'$'}{order.status}")
+        }
+
+        val cancelled = order.withStatus(OrderStatus.CANCELLED)
+        return orderRepository.save(cancelled)
+    }
+
+    fun refundOrder(orderId: String): Order {
+        val order = getOrder(orderId)

+
+        if (!order.canBeRefunded()) {
+            throw IllegalStateException("현재 상태에서는 환불할 수 없습니다: ${'$'}{order.status}")
+        }
+
+        val refunded = order.withStatus(OrderStatus.REFUNDED)
+        return orderRepository.save(refunded)
+    }
+
+    fun confirmOrder(orderId: String): Order {
+        val order = getOrder(orderId)
+
+        if (order.status != OrderStatus.PENDING) {
+            throw IllegalStateException("PENDING 상태의 주문만 확인 가능합니다: ${'$'}{order.status}")
+        }
+
+        val confirmed = order.withStatus(OrderStatus.CONFIRMED)
+        return orderRepository.save(confirmed)
+    }
+
+    fun getPendingOrders(): List<Order> = orderRepository.findAllByStatus(OrderStatus.PENDING)
+}
""".trimIndent()
    }

    @Autowired
    private lateinit var aiReviewPort: AiReviewPort

    @Autowired
    private lateinit var conventionContextService: ConventionContextService

    @Autowired
    private lateinit var conventionIndexUseCase: ConventionIndexUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    @BeforeEach
    fun setUp() {
        // 테스트 간 stub 오염 방지
        wireMock.resetAll()
        // OpenAI 임베딩 스텁 (reindex 및 buildContext 호출 시 필요)
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("openai-embedding-batch")
                )
        )
        // Langfuse 스텁 (리뷰 완료 후 관측 데이터 전송 시 필요)
        WireMockStubs.stubLangfuseIngestion(wireMock)
        // Redis 캐시 초기화 — 이전 리뷰 캐시 제거
        redisTemplate.connectionFactory
            .reactiveConnection
            .serverCommands()
            .flushAll()
            .block()
    }

    @Test
    fun `RAG 전후 리뷰 품질 비교`(): Unit = runBlocking {
        val filePath = "src/main/kotlin/stillframe42/codereviewertester/order/adapter/web/OrderController.kt"
        val query = "OrderController.kt"

        // Before: vector_store 비운 상태 → buildContext가 빈 문자열 반환 → conventionContext = null
        jdbcTemplate.execute("DELETE FROM vector_store")
        val contextBefore = conventionContextService.buildContext(query = query, filePath = filePath)
        val reviewBefore = aiReviewPort.reviewCode(
            code = DIFF,
            provider = AiProvider.ANTHROPIC,
            conventionContext = null,
        )
        writeResult(
            filename = "plans/202604-2w/rag-quality-before.md",
            label = "전",
            context = contextBefore,
            review = reviewBefore,
        )

        // After: reindex 후 buildContext → 아키텍처 컨벤션 포함 → conventionContext 주입
        conventionIndexUseCase.reindex()
        val contextAfter = conventionContextService.buildContext(query = query, filePath = filePath)
        val reviewAfter = aiReviewPort.reviewCode(
            code = DIFF,
            provider = AiProvider.ANTHROPIC,
            conventionContext = contextAfter.ifBlank { null },
        )
        writeResult(
            filename = "plans/202604-2w/rag-quality-after.md",
            label = "후",
            context = contextAfter,
            review = reviewAfter,
        )

        // 확인 기준: after 결과에 컨벤션 관련 키워드 1개 이상 포함
        val afterText = reviewAfter.summary +
            "\n" + reviewAfter.issues.joinToString("\n") { "${it.description} ${it.suggestion}" }
        assertThat(countKeywords(afterText))
            .withFailMessage("RAG 적용 후 리뷰에 컨벤션 관련 키워드가 포함되어야 합니다")
            .isGreaterThan(0)
    }

    // 리뷰 결과를 지정 파일에 마크다운 형식으로 저장한다
    private fun writeResult(filename: String, label: String, context: String, review: CodeReview) {
        val now = LocalDateTime.now()
        val fullText = review.summary +
            "\n" + review.issues.joinToString("\n") { "${it.description} ${it.suggestion}" }
        val keywords = listOf("UseCase", "Default", "포트", "port", "헥사고날", "hexagonal", "컨벤션")
        val countDetails = keywords.joinToString("\n") { kw ->
            "- \"$kw\": ${fullText.split(kw, ignoreCase = true).size - 1}회"
        }
        File(filename).writeText(buildString {
            appendLine("# RAG 적용 ${label} 리뷰 결과")
            appendLine()
            appendLine("## 테스트 조건")
            appendLine("- PR: stillframe42/code-reviewer-tester#11")
            appendLine("- 파일: OrderController.kt, OrderService.kt")
            appendLine("- 실행일시: $now")
            appendLine()
            appendLine("## 주입된 컨벤션 컨텍스트")
            appendLine(if (context.isBlank()) "없음" else context)
            appendLine()
            appendLine("## AI 리뷰 결과")
            appendLine("**총점:** ${review.overallScore}/10")
            appendLine()
            appendLine("**총평:**")
            appendLine(review.summary)
            appendLine()
            appendLine("**이슈 목록:**")
            review.issues.forEach { issue ->
                appendLine("- [${issue.severity}] ${issue.description}")
                appendLine("  - 제안: ${issue.suggestion}")
            }
            appendLine()
            appendLine("## 컨벤션 관련 피드백 언급 횟수")
            appendLine(countDetails)
            appendLine("- 합계: ${countKeywords(fullText)}회")
        })
    }

    // 지정 키워드의 총 출현 횟수를 합산하여 반환한다
    private fun countKeywords(text: String): Int =
        listOf("UseCase", "Default", "포트", "port", "헥사고날", "hexagonal", "컨벤션")
            .sumOf { kw -> text.split(kw, ignoreCase = true).size - 1 }
}
