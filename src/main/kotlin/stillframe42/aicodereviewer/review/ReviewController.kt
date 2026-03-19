package stillframe42.aicodereviewer.review

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.chat.AiProvider
import stillframe42.aicodereviewer.review.dto.CodeReviewResult
import stillframe42.aicodereviewer.review.dto.ReviewRequest

@RestController
@RequestMapping("/api/review")
class ReviewController(private val reviewService: ReviewService) {

    @PostMapping
    suspend fun review(@RequestBody @Valid request: ReviewRequest): ResponseEntity<CodeReviewResult> =
        ResponseEntity.ok(
            reviewService.reviewCode(request.code, request.provider ?: AiProvider.ANTHROPIC)
        )
}
