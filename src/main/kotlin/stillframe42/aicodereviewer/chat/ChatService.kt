package stillframe42.aicodereviewer.chat

interface ChatService {

    // Anthropic Claude에게 메시지를 전달하고 응답을 반환
    fun chat(message: String): String
}
