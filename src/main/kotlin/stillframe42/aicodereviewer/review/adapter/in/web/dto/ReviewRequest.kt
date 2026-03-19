package stillframe42.aicodereviewer.review.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions

data class ReviewRequest(
    @field:NotBlank(message = "코드를 입력해주세요")
    val code: String,

    // AI 프로바이더 (JSON에서 생략 시 null → 컨트롤러에서 ANTHROPIC 기본값 처리)
    val provider: AiProvider? = null,

    // diff 전처리 옵션 (null이면 전처리 없이 raw code 전달)
    val diffOptions: DiffFilterOptions? = null,
)
