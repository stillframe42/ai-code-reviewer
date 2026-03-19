package stillframe42.aicodereviewer.chat.adapter.`in`.web

import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.chat.adapter.`in`.web.dto.ChatRequest
import stillframe42.aicodereviewer.chat.adapter.`in`.web.dto.ChatResponse
import stillframe42.aicodereviewer.chat.domain.port.`in`.ChatUseCase
import stillframe42.aicodereviewer.core.AiProvider

@RestController
@RequestMapping("/api/chat")
@Validated
class ChatController(private val chatUseCase: ChatUseCase) {

    @PostMapping
    suspend fun chat(@RequestBody @Valid request: ChatRequest): ResponseEntity<ChatResponse> =
        ResponseEntity.ok(ChatResponse(answer = chatUseCase.chat(request.message, request.provider ?: AiProvider.ANTHROPIC)))

    // SSE 스트리밍 엔드포인트: AI 응답 토큰을 text/event-stream 형식으로 실시간 전송
    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@RequestBody @Valid request: ChatRequest): Flow<ServerSentEvent<String>> =
        chatUseCase.streamChat(request.message, request.provider ?: AiProvider.ANTHROPIC)
            .map { token -> ServerSentEvent.builder<String>().data(token).build() }
}
