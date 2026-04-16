package stillframe42.aicodereviewer.review.application

import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.metrics.ReviewMetrics
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.common.observability.withSpan
import stillframe42.aicodereviewer.review.domain.service.AiModelSelector
import stillframe42.aicodereviewer.config.ReviewProperties
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStore
import stillframe42.aicodereviewer.rag.application.ConventionContextService
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStatsStore
import stillframe42.aicodereviewer.review.domain.service.DiffPreprocessor
import stillframe42.aicodereviewer.review.domain.service.PrImportanceAnalyzer

// 코드 리뷰 유스케이스 구현 — AI 포트에 위임하며, 캐싱·모델 선택 등 비즈니스 로직을 조합한다.
@Service
class DefaultReviewService(
    private val aiReviewPort: AiReviewPort,
    private val diffPreprocessor: DiffPreprocessor,
    private val reviewProperties: ReviewProperties,
    private val prImportanceAnalyzer: PrImportanceAnalyzer,
    private val aiModelSelector: AiModelSelector,
    private val reviewCacheStore: ReviewCacheStore,
    private val reviewMetrics: ReviewMetrics,
    private val reviewCacheStatsStore: ReviewCacheStatsStore,
    private val conventionContextService: ConventionContextService,
    private val observabilityPort: ObservabilityPort,
) : ReviewUseCase, Logging {

    override suspend fun reviewCode(
        code: String,
        provider: AiProvider,
        diffOptions: DiffFilterOptions?,
        mode: ReviewMode,
    ): CodeReview = withContext(observabilityPort.traceContext()) {
        observabilityPort.withSpan(
            name = "review.root",
            input = mapOf("provider" to provider.name, "mode" to (mode::class.simpleName ?: "Unknown"), "hasDiffOptions" to (diffOptions != null)),
            outputMapper = { review: CodeReview ->
                mapOf(
                    "overallScore" to review.overallScore,
                    "issueCount" to review.issues.size,
                    "modelName" to (review.modelName ?: "unknown"),
                )
            },
        ) {
            val options = diffOptions ?: return@withSpan aiReviewPort.reviewCode(code, provider, mode, reviewContext = null, modelName = null)

            val merged = options.copy(
                additionalExcludePatterns = options.additionalExcludePatterns +
                    reviewProperties.diff.additionalExcludePatterns,
                maxTokens = options.maxTokens ?: reviewProperties.diff.maxTokens,
            )
            val preprocessResult = diffPreprocessor.preprocess(code, merged)
            logger.debug("=== 전처리된 diff (AI 전달 내용) ===\n{}", preprocessResult.diff)

            val importance = prImportanceAnalyzer.analyze(preprocessResult.fileNames)
            val modelName = aiModelSelector.selectModel(importance)
            logger.info("PR 중요도: {}, 선택 모델: {}", importance, modelName)

            val fileDiffs = preprocessResult.fileDiffs.filter { it.isNotBlank() }
            val review = if (fileDiffs.size > 1)
                reviewParallel(fileDiffs, provider, mode, modelName)
            else
                reviewWithCache(preprocessResult.diff, provider, mode, modelName,
                    filePath = preprocessResult.fileNames.singleOrNull())

            review.copy(modelName = modelName)
        }
    }

    // 캐시 조회 → 히트 시 즉시 반환, 미스 시 RAG 호출 후 AI 호출 후 캐시 저장
    private suspend fun reviewWithCache(
        diff: String,
        provider: AiProvider,
        mode: ReviewMode,
        modelName: String?,
        filePath: String? = null,
    ): CodeReview {
        val key = cacheKey(diff)
        val cached = reviewCacheStore.get(key)
        if (cached != null) {
            logger.debug("캐시 히트: key={}", key)
            reviewMetrics.recordCacheHit()
            reviewCacheStatsStore.incrementHit()
            return cached
        }
        logger.debug("캐시 미스: key={}", key)
        reviewMetrics.recordCacheMiss()
        reviewCacheStatsStore.incrementMiss()
        // 캐시 미스 시에만 RAG 호출 (캐시 히트는 이미 컨벤션 컨텍스트가 반영된 결과)
        val conventionContext = filePath?.let {
            conventionContextService.buildContext(
                query = it.substringAfterLast("/"),
                filePath = it,
            )
        }
        return aiReviewPort.reviewCode(diff, provider, mode, reviewContext = null, modelName = modelName,
            conventionContext = conventionContext)
            .also { result ->
                // 캐시 저장 실패는 리뷰 결과 반환에 영향을 주지 않는다 (best-effort)
                runCatching { reviewCacheStore.put(key, result) }
                    .onFailure { e -> logger.warn("캐시 저장 실패 (무시): {}", e.message) }
            }
    }

    // Semaphore로 동시 호출 수를 제한하며 병렬 LLM 호출 후 결과 집계
    // supervisorScope: 개별 파일 리뷰 실패가 다른 파일 취소로 이어지지 않도록 격리
    private suspend fun reviewParallel(
        fileDiffs: List<String>,
        provider: AiProvider,
        mode: ReviewMode,
        modelName: String?,
    ): CodeReview {
        val concurrency = reviewProperties.diff.maxConcurrency
        logger.info("파일별 병렬 리뷰 시작: {}개 파일 (최대 동시 호출: {})", fileDiffs.size, concurrency)
        val semaphore = Semaphore(concurrency)
        return supervisorScope {
            fileDiffs.map { diff ->
                async {
                    semaphore.withPermit {
                        val filePath = extractFilePath(diff)
                        reviewWithCache(diff, provider, mode, modelName, filePath)
                    }
                }
            }
        }
            .mapNotNull { deferred ->
                runCatching { deferred.await() }
                    .onFailure { e -> logger.warn("파일 리뷰 실패 (스킵): {}", e.message) }
                    .getOrNull()
            }
            .takeIf { it.isNotEmpty() }
            ?.let(::aggregate)
            ?: throw IllegalStateException("모든 파일(${fileDiffs.size}개) 리뷰가 실패했습니다")
    }

    private fun aggregate(reviews: List<CodeReview>): CodeReview {
        if (reviews.size == 1) return reviews.first()
        return CodeReview(
            overallScore = reviews.map(CodeReview::overallScore).average().toInt().coerceIn(0, 10),
            summary = reviews.joinToString("\n") { it.summary },
            issues = reviews.flatMap { it.issues },
            positives = reviews.flatMap { it.positives }.distinct().take(3),
            toolCallCount = reviews.sumOf { it.toolCallCount },
        )
    }

    // diff 청크의 --- a/ 또는 +++ b/ 헤더에서 파일 경로를 추출한다
    // DiffPreprocessResult.fileNames와 동일한 로직
    private fun extractFilePath(diff: String): String? =
        diff.lineSequence()
            .firstOrNull { it.startsWith("--- a/") }
            ?.removePrefix("--- a/")
            ?: diff.lineSequence()
                .firstOrNull { it.startsWith("+++ b/") }
                ?.removePrefix("+++ b/")

    // diff 내용의 SHA-256 해시로 캐시 키 생성
    // 동일 파일 + 동일 headSha → diff 내용 동일 → 해시 동일 (의미상 repoFullName:filePath:headSha와 동등)
    private fun cacheKey(diff: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(diff.toByteArray(Charsets.UTF_8))
        return "review:cache:" + digest.joinToString("") { "%02x".format(it) }
    }
}
