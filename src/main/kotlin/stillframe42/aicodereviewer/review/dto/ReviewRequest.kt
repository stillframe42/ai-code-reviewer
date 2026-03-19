package stillframe42.aicodereviewer.review.dto

import jakarta.validation.constraints.NotBlank
import stillframe42.aicodereviewer.chat.AiProvider

data class ReviewRequest(
    @field:NotBlank(message = "코드를 입력해주세요")
    val code: String,

    // AI 프로바이더 (JSON에서 생략 시 null → 컨트롤러에서 ANTHROPIC 기본값 처리)
    val provider: AiProvider? = null
)
