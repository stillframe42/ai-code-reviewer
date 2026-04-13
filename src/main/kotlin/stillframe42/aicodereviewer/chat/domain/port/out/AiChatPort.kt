package stillframe42.aicodereviewer.chat.domain.port.out

import kotlinx.coroutines.flow.Flow
import stillframe42.aicodereviewer.core.AiProvider

// 채팅 기능 출력 포트 — 도메인이 AI 인프라에 요청하는 인터페이스
interface AiChatPort {
    suspend fun chat(
        message: String,
        provider: AiProvider,
        conventionContext: String? = null,
    ): String

    fun streamChat(
        message: String,
        provider: AiProvider,
        conventionContext: String? = null,
    ): Flow<String>
}
