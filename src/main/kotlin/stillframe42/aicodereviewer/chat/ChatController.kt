package stillframe42.aicodereviewer.chat

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.chat.dto.ChatRequest
import stillframe42.aicodereviewer.chat.dto.ChatResponse

@RestController
@RequestMapping("/api/chat")
@Validated
class ChatController(private val chatService: ChatService) {

    @PostMapping
    fun chat(@RequestBody @Valid request: ChatRequest): ResponseEntity<ChatResponse> =
        ResponseEntity.ok(ChatResponse(answer = chatService.chat(request.message, request.provider ?: AiProvider.ANTHROPIC)))
}
