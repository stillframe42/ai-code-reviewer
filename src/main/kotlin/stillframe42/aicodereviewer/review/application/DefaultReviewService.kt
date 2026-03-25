package stillframe42.aicodereviewer.review.application

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.ReviewProperties
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort
import stillframe42.aicodereviewer.review.domain.service.DiffPreprocessor

// 코드 리뷰 유스케이스 구현 — AI 포트에 위임하며, 향후 이력 저장·사용량 제한 등 비즈니스 로직이 추가되는 레이어
@Service
class DefaultReviewService(
    private val aiReviewPort: AiReviewPort,
    private val diffPreprocessor: DiffPreprocessor,
    private val reviewProperties: ReviewProperties,
) : ReviewUseCase, Logging {

    override suspend fun reviewCode(
        code: String,
        provider: AiProvider,
        diffOptions: DiffFilterOptions?,
    ): CodeReview {
        // diffOptions가 없으면 전처리 없이 바로 AI 호출
        val options = diffOptions ?: return aiReviewPort.reviewCode(code, provider)

        // 요청 옵션에 외부 설정값을 병합 (요청값 우선, 패턴은 합산)
        val merged = options.copy(
            additionalExcludePatterns = options.additionalExcludePatterns +
                reviewProperties.diff.additionalExcludePatterns,
            maxTokens = options.maxTokens ?: reviewProperties.diff.maxTokens,
        )
        val preprocessResult = diffPreprocessor.preprocess(code, merged)
        logger.debug("=== 전처리된 diff (AI 전달 내용) ===\n{}", preprocessResult.diff)

        val fileDiffs = preprocessResult.fileDiffs.filter { it.isNotBlank() }
        return if (fileDiffs.size > 1) {
            // 파일 2개 이상 — Semaphore로 동시 호출 수를 제한하며 병렬 LLM 호출 후 결과 집계
            val concurrency = reviewProperties.diff.maxConcurrency
            logger.info("파일별 병렬 리뷰 시작: {}개 파일 (최대 동시 호출: {})", fileDiffs.size, concurrency)
            val semaphore = Semaphore(concurrency)
            coroutineScope { fileDiffs.map { async { semaphore.withPermit { aiReviewPort.reviewCode(it, provider) } } } }
                .awaitAll()
                .let(::aggregate)
        } else {
            // 파일 1개 이하 — 단일 호출
            aiReviewPort.reviewCode(preprocessResult.diff, provider)
        }
    }

    // 파일별 리뷰 결과를 하나의 CodeReview로 집계한다
    private fun aggregate(reviews: List<CodeReview>): CodeReview {
        if (reviews.size == 1) return reviews.first()
        return CodeReview(
            overallScore = reviews.map { it.overallScore }.average().toInt().coerceIn(0, 10),
            summary = reviews.joinToString("\n") { it.summary },
            issues = reviews.flatMap { it.issues },
            positives = reviews.flatMap { it.positives }.distinct().take(3),
        )
    }

}
