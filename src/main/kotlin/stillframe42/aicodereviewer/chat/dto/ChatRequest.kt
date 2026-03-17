package stillframe42.aicodereviewer.chat.dto

import jakarta.validation.constraints.NotBlank

data class ChatRequest(
    @field:NotBlank(message = "메시지를 입력해주세요")
    val message: String
)
