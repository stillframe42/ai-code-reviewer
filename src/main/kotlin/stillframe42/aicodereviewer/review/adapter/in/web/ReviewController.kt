package stillframe42.aicodereviewer.review.adapter.`in`.web

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.CodeReviewResponse
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewModeRequest
import stillframe42.aicodereviewer.review.adapter.`in`.web.dto.ReviewRequest
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

@RestController
@RequestMapping("/api/review")
class ReviewController(private val reviewUseCase: ReviewUseCase) {

    @PostMapping
    fun review(@RequestBody @Valid request: ReviewRequest): ResponseEntity<CodeReviewResponse> {
        val mode = when (request.reviewMode) {
            null, ReviewModeRequest.WITHOUT_TOOLS -> ReviewMode.Simple
            ReviewModeRequest.WITH_TOOLS -> {
                val id = request.installationId
                    ?: return ResponseEntity.badRequest().build()
                ReviewMode.WithGitHubTools(id)
            }
        }
        return ResponseEntity.ok(
            CodeReviewResponse.from(
                reviewUseCase.reviewCode(
                    code = request.code,
                    provider = request.provider ?: AiProvider.ANTHROPIC,
                    diffOptions = request.diffOptions,
                    mode = mode,
                )
            )
        )
    }
}
