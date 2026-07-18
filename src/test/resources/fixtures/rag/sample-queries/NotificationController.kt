package stillframe42.codereviewertester.notification.adapter.web

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.codereviewertester.notification.adapter.web.dto.NotificationRequest
import stillframe42.codereviewertester.notification.adapter.web.dto.NotificationResponse
import stillframe42.codereviewertester.notification.application.NotificationService

@RestController
@RequestMapping("/api/notifications")
@Validated
class NotificationController(private val notificationService: NotificationService) {

    @PostMapping
    fun send(@RequestBody @Valid request: NotificationRequest): ResponseEntity<NotificationResponse> =
        ResponseEntity.ok(notificationService.send(request.channel, request.message))

    @PostMapping("/broadcast")
    fun broadcast(@RequestBody @Valid request: NotificationRequest): ResponseEntity<NotificationResponse> =
        ResponseEntity.ok(notificationService.broadcast(request.message))
}
