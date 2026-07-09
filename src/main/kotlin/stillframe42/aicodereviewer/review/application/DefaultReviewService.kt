package stillframe42.aicodereviewer.review.application

import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.exception.AiResponseException
import stillframe42.aicodereviewer.common.observability.ObservabilityPort
import stillframe42.aicodereviewer.common.observability.withSpan
import stillframe42.aicodereviewer.review.domain.service.AiModelSelector
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.config.ReviewProperties
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCachePort
import stillframe42.aicodereviewer.config.RagProperties
import stillframe42.aicodereviewer.rag.application.ConventionContextService
import stillframe42.aicodereviewer.rag.domain.service.PatchQueryExtractor
import stillframe42.aicodereviewer.review.domain.service.DiffPreprocessor
import stillframe42.aicodereviewer.review.domain.service.PrImportanceAnalyzer

// 코드 리뷰 유스케이스 구현 — AI 포트에 위임하며, 캐싱·모델 선택 등 비즈니스 로직을 조합한다.
@Service
class DefaultReviewService(
    private val aiReviewPort: AiReviewPort,
    private val reviewProperties: ReviewProperties,
    private val prImportanceAnalyzer: PrImportanceAnalyzer,
    private val aiModelSelector: AiModelSelector,
    private val reviewCachePort: ReviewCachePort,
    private val conventionContextService: ConventionContextService,
    private val observabilityPort: ObservabilityPort,
    private val claimVerifier: ClaimVerifier,
    private val ragProperties: RagProperties,
    private val aiReviewerProperties: AiReviewerProperties,
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
            val preprocessResult = DiffPreprocessor.preprocess(code, merged)
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
    // 메트릭/로그/저장 best-effort 처리는 MeteredReviewCacheAdapter 데코레이터에 위임한다.
    private suspend fun reviewWithCache(
        diff: String,
        provider: AiProvider,
        mode: ReviewMode,
        modelName: String?,
        filePath: String? = null,
    ): CodeReview {
        val key = cacheKey(diff, provider, modelName, mode)
        reviewCachePort.get(key)?.let { return it }

        // 캐시 미스 시에만 RAG 호출 (캐시 히트는 이미 컨벤션 컨텍스트가 반영된 결과)
        val conventionContext = filePath?.let {
            conventionContextService.buildContext(
                query = PatchQueryExtractor.extract(diff, filePath = it),
                filePath = it,
            )
        }
        val rawReview = aiReviewPort.reviewCode(
            diff, provider, mode, reviewContext = null, modelName = modelName,
            conventionContext = conventionContext,
        )
        // claimVerifyEnabled 시 컨벤션 컨텍스트 기반으로 issues 사후 검증·필터링
        val finalReview = if (ragProperties.claimVerifyEnabled && !conventionContext.isNullOrBlank()) {
            val verifiedIssues = claimVerifier.verify(rawReview.issues, conventionContext)
            rawReview.copy(issues = verifiedIssues)
        } else {
            rawReview
        }
        return finalReview.also { reviewCachePort.put(key, it) }
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
            ?: throw AiResponseException("모든 파일(${fileDiffs.size}개) 리뷰가 실패했습니다")
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

    // 결과에 영향을 주는 모든 결정 변수를 해시 입력에 포함하여 캐시 키를 생성한다.
    // - diff: 입력 코드 자체
    // - provider / modelName: 동일 diff라도 모델이 다르면 결과가 다르므로 분리해야 한다
    // - mode 종류: Simple vs WithGitHubTools — Tool Calling 활성 여부에 따라 결과가 달라진다
    //   (mode 안의 installationId는 결과에 영향 없으므로 키에 포함하지 않는다)
    // prefix 의 keyVersion 은 키 외부 결정 요인(프롬프트, RAG 인덱스 등)이 바뀔 때
    // 운영자가 수동으로 올려 기존 캐시를 일괄 무효화하기 위한 손잡이다.
    private fun cacheKey(
        diff: String,
        provider: AiProvider,
        modelName: String?,
        mode: ReviewMode,
    ): String {
        val payload = buildString {
            append(diff)
            append("|provider=").append(provider.name)
            append("|model=").append(modelName ?: "default")
            append("|mode=").append(mode::class.simpleName ?: "Unknown")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        val version = aiReviewerProperties.cache.keyVersion
        return "review:cache:$version:" + digest.joinToString("") { "%02x".format(it) }
    }
}
