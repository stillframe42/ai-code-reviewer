package stillframe42.aicodereviewer.chat.domain.port.`in`

import kotlinx.coroutines.flow.Flow
import stillframe42.aicodereviewer.core.AiProvider

// 채팅 기능 입력 포트 — 도메인이 외부에 제공하는 유스케이스 인터페이스
interface ChatUseCase {
    suspend fun chat(message: String, provider: AiProvider): String
    fun streamChat(message: String, provider: AiProvider): Flow<String>
}
