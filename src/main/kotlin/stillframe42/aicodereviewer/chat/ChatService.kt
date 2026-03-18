package stillframe42.aicodereviewer.chat

import kotlinx.coroutines.flow.Flow

interface ChatService {

    // 지정된 AI 프로바이더에게 메시지를 전달하고 응답을 반환 (코루틴 비동기)
    suspend fun chat(message: String, provider: AiProvider): String

    // 지정된 AI 프로바이더에게 메시지를 전달하고 토큰 단위 스트리밍 응답을 반환
    fun streamChat(message: String, provider: AiProvider): Flow<String>
}
