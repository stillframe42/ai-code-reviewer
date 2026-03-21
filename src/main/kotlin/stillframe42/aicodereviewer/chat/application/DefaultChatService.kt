package stillframe42.aicodereviewer.chat.application

import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.chat.domain.port.`in`.ChatUseCase
import stillframe42.aicodereviewer.chat.domain.port.out.AiChatPort
import stillframe42.aicodereviewer.core.AiProvider

// 채팅 유스케이스 구현 — AI 포트에 위임하며, 향후 이력 저장·사용량 제한 등 비즈니스 로직이 추가되는 레이어
@Service
class DefaultChatService(private val aiChatPort: AiChatPort) : ChatUseCase {

    override suspend fun chat(message: String, provider: AiProvider): String =
        aiChatPort.chat(message, provider)

    override fun streamChat(message: String, provider: AiProvider): Flow<String> =
        aiChatPort.streamChat(message, provider)
}
