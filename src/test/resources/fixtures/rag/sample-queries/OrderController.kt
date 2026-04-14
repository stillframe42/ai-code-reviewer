package stillframe42.codereviewertester.order.adapter.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import stillframe42.codereviewertester.order.application.OrderService
import stillframe42.codereviewertester.order.domain.Order

@RestController
@RequestMapping("/orders")
class OrderController(private val orderService: OrderService) {

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<Order> {
        val order = orderService.getOrder(orderId)
        return ResponseEntity.ok(order)
    }

    @GetMapping
    fun getOrdersByCustomer(@RequestParam customerId: String): ResponseEntity<List<Order>> {
        val orders = orderService.getOrdersByCustomer(customerId)
        return ResponseEntity.ok(orders)
    }

    @PostMapping("/{orderId}/confirm")
    fun confirmOrder(@PathVariable orderId: String): ResponseEntity<Order> {
        val confirmed = orderService.confirmOrder(orderId)
        return ResponseEntity.ok(confirmed)
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(@PathVariable orderId: String): ResponseEntity<Order> {
        val cancelled = orderService.cancelOrder(orderId)
        return ResponseEntity.ok(cancelled)
    }

    @PostMapping("/{orderId}/refund")
    fun refundOrder(@PathVariable orderId: String): ResponseEntity<Order> {
        val refunded = orderService.refundOrder(orderId)
        return ResponseEntity.ok(refunded)
    }

    @GetMapping("/pending")
    fun getPendingOrders(): ResponseEntity<List<Order>> {
        val pending = orderService.getPendingOrders()
        return ResponseEntity.ok(pending)
    }
}
