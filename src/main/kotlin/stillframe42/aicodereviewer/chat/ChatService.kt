package stillframe42.aicodereviewer.chat

interface ChatService {

    // 지정된 AI 프로바이더에게 메시지를 전달하고 응답을 반환
    fun chat(message: String, provider: AiProvider): String
}
