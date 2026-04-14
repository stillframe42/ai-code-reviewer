package stillframe42.codereviewertester.order.application

import org.springframework.stereotype.Service
import stillframe42.codereviewertester.order.domain.Order
import stillframe42.codereviewertester.order.domain.OrderRepository
import stillframe42.codereviewertester.order.domain.OrderStatus

@Service
class OrderService(private val orderRepository: OrderRepository) {

    fun getOrder(orderId: String): Order {
        return orderRepository.findById(orderId)
            ?: throw IllegalArgumentException("주문을 찾을 수 없습니다: $orderId")
    }

    fun getOrdersByCustomer(customerId: String): List<Order> {
        return orderRepository.findAllByCustomerId(customerId)
    }

    fun cancelOrder(orderId: String): Order {
        val order = getOrder(orderId)

        if (!order.canBeCancelled()) {
            throw IllegalStateException("현재 상태에서는 취소할 수 없습니다: ${order.status}")
        }

        val cancelled = order.withStatus(OrderStatus.CANCELLED)
        return orderRepository.save(cancelled)
    }

    fun refundOrder(orderId: String): Order {
        val order = getOrder(orderId)

        if (!order.canBeRefunded()) {
            throw IllegalStateException("현재 상태에서는 환불할 수 없습니다: ${order.status}")
        }

        val refunded = order.withStatus(OrderStatus.REFUNDED)
        return orderRepository.save(refunded)
    }

    fun confirmOrder(orderId: String): Order {
        val order = getOrder(orderId)

        if (order.status != OrderStatus.PENDING) {
            throw IllegalStateException("PENDING 상태의 주문만 확인 가능합니다: ${order.status}")
        }

        val confirmed = order.withStatus(OrderStatus.CONFIRMED)
        return orderRepository.save(confirmed)
    }

    fun getPendingOrders(): List<Order> = orderRepository.findAllByStatus(OrderStatus.PENDING)
}
