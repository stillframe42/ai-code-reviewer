// 픽스처: N+1 쿼리 문제 — PERFORMANCE/MAJOR 이슈 감지 테스트용
// 기대: PERFORMANCE 카테고리, MAJOR 이상 심각도 이슈 최소 1개

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
) {

    // 문제: 주문 목록 조회 후 루프 내에서 각 주문마다 개별 DB 조회 (N+1)
    fun getOrderSummaries(): List<OrderSummary> {
        val orders = orderRepository.findAll()  // 1번 쿼리

        return orders.map { order ->
            val product = productRepository.findById(order.productId)  // N번 쿼리 (주문 수만큼)
            val user = userRepository.findById(order.userId)            // N번 쿼리 (주문 수만큼)

            OrderSummary(
                orderId = order.id,
                productName = product?.name ?: "알 수 없음",
                userName = user?.name ?: "알 수 없음",
                amount = order.amount,
            )
        }
    }

    // 문제: 통계 계산에도 동일한 N+1 패턴 반복
    fun calculateUserStats(userIds: List<Long>): Map<Long, Int> {
        return userIds.associateWith { userId ->
            orderRepository.countByUserId(userId)  // N번 쿼리 (userId 수만큼)
        }
    }
}
