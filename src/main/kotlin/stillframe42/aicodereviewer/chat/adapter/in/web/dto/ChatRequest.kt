package stillframe42.aicodereviewer.chat.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import stillframe42.aicodereviewer.core.AiProvider

data class ChatRequest(
    @field:NotBlank(message = "메시지를 입력해주세요")
    val message: String,

    // AI 프로바이더 (JSON에서 생략 시 null → 컨트롤러에서 ANTHROPIC 기본값 처리)
    val provider: AiProvider? = null
)
