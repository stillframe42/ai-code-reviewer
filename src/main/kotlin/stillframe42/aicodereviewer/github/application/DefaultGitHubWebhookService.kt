package stillframe42.aicodereviewer.github.application

import kotlinx.coroutines.CancellationException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.agent.application.AgentFallbackMetrics
import stillframe42.aicodereviewer.agent.application.AgentReviewService
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisFailedException
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisTimeoutException
import stillframe42.aicodereviewer.agent.domain.exception.AgentException
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.agent.domain.service.SecurityFileDetector
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.metrics.event.ReviewCompletedEvent
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrReview
import stillframe42.aicodereviewer.github.domain.model.PrReviewEvent
import stillframe42.aicodereviewer.github.domain.model.PrReviewLineComment
import stillframe42.aicodereviewer.github.domain.model.PullRequestEvent
import stillframe42.aicodereviewer.github.domain.port.`in`.GitHubWebhookUseCase
import stillframe42.aicodereviewer.github.domain.port.out.GitHubApiPort
import stillframe42.aicodereviewer.github.domain.port.out.ProcessedEventPort
import stillframe42.aicodereviewer.github.domain.port.out.ReviewCommentFormatterPort
import stillframe42.aicodereviewer.github.domain.service.DiffPositionResolver
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.ReviewPersistencePort
import java.time.Instant

// GitHub Webhook 유스케이스 구현 — PR 이벤트 수신 시 diff 조회 → AI 리뷰 → 코멘트 등록 흐름을 조율한다
@Service
class DefaultGitHubWebhookService(
    private val gitHubApiPort: GitHubApiPort,
    private val reviewUseCase: ReviewUseCase,
    private val reviewCommentFormatterPort: ReviewCommentFormatterPort,
    private val processedEventPort: ProcessedEventPort,
    private val diffPositionResolver: DiffPositionResolver,
    private val reviewPersistencePort: ReviewPersistencePort,
    private val eventPublisher: ApplicationEventPublisher,
    private val agentReviewService: AgentReviewService,
    private val agentFallbackMetrics: AgentFallbackMetrics,
) : GitHubWebhookUseCase, Logging {

    // diff position 매핑 + 포맷팅이 완료된 PR 코멘트 구성용 출력
    private data class ReviewOutput(
        val body: String,
        val lineComments: List<PrReviewLineComment>,
        val hasNoIssues: Boolean,
    )

    // PR 처리 입력 — diff + 변경 파일 목록
    private data class PrInputs(
        val diff: String,
        val files: List<PrFile>,
    )

    override suspend fun handlePullRequestEvent(event: PullRequestEvent) {
        logger.info(
            "PR 이벤트 처리 시작: repo={}, pr={}, action={}",
            event.repositoryFullName, event.pullRequestNumber, event.action,
        )

        if (skipIfAlreadyProcessed(event)) return
        val inputs = fetchPrInputsOrNull(event) ?: return

        val startNanos = System.nanoTime()
        val reviewRequestId = startPersistedReview(event)
        val review = runAiReview(event, inputs.diff, inputs.files)
        finalizePersistedReview(reviewRequestId, review)

        dismissPreviousReview(event)
        val newReviewId = submitReviewToGitHub(event, review, inputs.diff)
        publishCompletedEvent(event, review, startNanos)

        if (review == null) return
        markProcessed(event, newReviewId)
    }

    // 0단계: 중복 처리 방지 — 동일 (레포, PR번호, SHA) 조합은 스킵
    private suspend fun skipIfAlreadyProcessed(event: PullRequestEvent): Boolean {
        if (!processedEventPort.isAlreadyProcessed(
                repositoryFullName = event.repositoryFullName,
                pullRequestNumber = event.pullRequestNumber,
                headSha = event.headSha,
            )
        ) return false

        logger.info(
            "이미 처리된 이벤트, 스킵: repo={}, pr={}, sha={}",
            event.repositoryFullName, event.pullRequestNumber, event.headSha,
        )
        return true
    }

    // 1단계: PR diff + 변경 파일 목록 조회. diff 가 비어 있으면 null 반환 (메타데이터만 변경된 PR)
    private suspend fun fetchPrInputsOrNull(event: PullRequestEvent): PrInputs? {
        val prDiff = gitHubApiPort.getPrDiff(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            installationId = event.installationId,
        )

        if (prDiff.isBlank()) {
            logger.warn(
                "PR diff가 비어 있어 리뷰를 건너뜁니다: repo={}, pr={}",
                event.repositoryFullName, event.pullRequestNumber,
            )
            return null
        }

        val prFiles = gitHubApiPort.getPrFiles(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            installationId = event.installationId,
        )

        return PrInputs(prDiff, prFiles)
    }

    // 2~3단계: 리뷰 요청 PENDING 저장 후 PROCESSING 으로 전환. 저장 실패가 리뷰 흐름을 중단시키지 않도록 격리
    private suspend fun startPersistedReview(event: PullRequestEvent): Long? {
        val reviewRequestId = runOrWarn("리뷰 요청 저장 실패 (리뷰는 계속 진행)") {
            reviewPersistencePort.saveReviewRequest(
                repoFullName = event.repositoryFullName,
                prNumber = event.pullRequestNumber,
                headSha = event.headSha,
            )
        } ?: return null

        runOrWarn("리뷰 상태 업데이트 실패") {
            reviewPersistencePort.updateReviewStatus(reviewRequestId, ReviewRequestStatus.PROCESSING)
        }
        return reviewRequestId
    }

    // 5단계: 리뷰 결과 저장 + 상태 종료. saveReviewResult 실패 시에도 상태 업데이트(DONE)가 반드시 실행되도록 블록을 분리한다
    private suspend fun finalizePersistedReview(reviewRequestId: Long?, review: CodeReview?) {
        if (reviewRequestId == null) return
        val now = Instant.now()
        val status = if (review != null) {
            runOrWarn("리뷰 결과 저장 실패") {
                reviewPersistencePort.saveReviewResult(reviewRequestId, review, review.modelName)
            }
            ReviewRequestStatus.DONE
        } else {
            ReviewRequestStatus.FAILED
        }
        runOrWarn("리뷰 상태 $status 업데이트 실패") {
            reviewPersistencePort.updateReviewStatus(reviewRequestId, status, now)
        }
    }

    // 6단계: 이전 리뷰 dismiss — 실패해도 새 리뷰 등록은 계속 진행
    private suspend fun dismissPreviousReview(event: PullRequestEvent) {
        val previousReviewId = processedEventPort.findLatestReviewId(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
        ) ?: return

        runOrWarn(
            "이전 리뷰 dismiss 실패 (새 리뷰 등록은 계속 진행): " +
                "repo=${event.repositoryFullName}, pr=${event.pullRequestNumber}, reviewId=$previousReviewId",
        ) {
            gitHubApiPort.dismissPrReview(
                repositoryFullName = event.repositoryFullName,
                pullRequestNumber = event.pullRequestNumber,
                reviewId = previousReviewId,
                installationId = event.installationId,
            )
        }
    }

    // 7~8단계: PR Reviews API 등록. 리뷰 실패 시 에러 안내 코멘트로 등록
    private suspend fun submitReviewToGitHub(
        event: PullRequestEvent,
        review: CodeReview?,
        prDiff: String,
    ): Long {
        val prReview = review?.let { buildReviewOutput(it, prDiff) }?.let { output ->
            PrReview(
                body = output.body,
                event = if (output.hasNoIssues) PrReviewEvent.APPROVE else PrReviewEvent.REQUEST_CHANGES,
                lineComments = output.lineComments,
                commitId = event.headSha,
            )
        } ?: PrReview(body = "⚠️ 코드 리뷰 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")

        return gitHubApiPort.postPrReview(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            review = prReview,
            installationId = event.installationId,
        )
    }

    // 8단계 끝: 메트릭 기록은 MetricsEventListener가 담당
    private fun publishCompletedEvent(event: PullRequestEvent, review: CodeReview?, startNanos: Long) {
        eventPublisher.publishEvent(
            ReviewCompletedEvent(
                repo = event.repositoryFullName,
                status = if (review != null) "DONE" else "FAILED",
                issues = review?.issues ?: emptyList(),
                durationNanos = System.nanoTime() - startNanos,
            )
        )
    }

    // 9단계: 처리 완료 기록 (중복 방지) — review_id 포함하여 저장. 리뷰 실패 시 호출 안 함 (다음 이벤트에서 재처리 허용)
    private suspend fun markProcessed(event: PullRequestEvent, newReviewId: Long) {
        processedEventPort.markAsProcessed(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            headSha = event.headSha,
            reviewId = newReviewId,
        )
    }

    // AI 리뷰 실행 — 보안 파일 포함 시 agent 경로 우선, AgentException 발생 시 Spring AI 경로로 폴백
    private suspend fun runAiReview(
        event: PullRequestEvent,
        prDiff: String,
        prFiles: List<PrFile>,
    ): CodeReview? {
        if (!SecurityFileDetector.hasSecurityFile(prFiles)) {
            return runDefaultReview(event, prDiff)
        }
        return try {
            agentReviewService.review(
                repositoryFullName = event.repositoryFullName,
                pullRequestNumber = event.pullRequestNumber,
                prDiff = prDiff,
                prFiles = prFiles,
            ).also {
                logger.info(
                    "Agent path 리뷰 완료: repo={}, pr={}, score={}",
                    event.repositoryFullName, event.pullRequestNumber, it.overallScore,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            fallbackToSpringAI(e.toFallbackReason(), event, prDiff, e)
        } catch (e: Exception) {
            // AgentException 외 외부 시스템 transient 오류 (RAG 임베딩 실패 등) 도 동일하게 폴백.
            // "error" reason 으로 메트릭 분리 — 의도된 도메인 예외와 구분 가능.
            fallbackToSpringAI("error", event, prDiff, e)
        }
    }

    // 분류된 reason 으로 메트릭/로그를 남기고 Spring AI 기본 리뷰 경로로 위임한다.
    private suspend fun fallbackToSpringAI(
        reason: String,
        event: PullRequestEvent,
        prDiff: String,
        cause: Throwable,
    ): CodeReview? {
        agentFallbackMetrics.record(reason)
        logger.warn(
            "Agent path 폴백: reason={}, repo={}, pr={}",
            reason, event.repositoryFullName, event.pullRequestNumber, cause,
        )
        // TODO(이벤트 큐 도입 후): cause is AgentAnalysisTimeoutException 일 때 scheduleRetry(event) 분기 추가
        return runDefaultReview(event, prDiff)
    }

    // Spring AI 기본 리뷰 경로 — agent 가 비활성화되거나 실패한 경우 사용
    private suspend fun runDefaultReview(event: PullRequestEvent, prDiff: String): CodeReview? =
        try {
            reviewUseCase.reviewCode(
                code = prDiff,
                provider = AiProvider.ANTHROPIC,
                diffOptions = DiffFilterOptions(),
                mode = ReviewMode.WithGitHubTools(event.installationId),
            ).also { review ->
                logger.info(
                    "AI 리뷰 생성 완료: repo={}, pr={}, score={}",
                    event.repositoryFullName, event.pullRequestNumber, review.overallScore,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("리뷰 생성 실패: repo={}, pr={}", event.repositoryFullName, event.pullRequestNumber, e)
            null
        }

    // diff position 매핑 + PR 코멘트 출력 구성
    private fun buildReviewOutput(review: CodeReview, prDiff: String): ReviewOutput {
        val resolution = diffPositionResolver.resolve(prDiff, review.issues)
        val bodyReview = review.copy(issues = resolution.unmappedIssues)
        // 인라인 코멘트 수 + 요약 이슈 수를 합산하여 총 이슈 수 계산
        val totalIssueCount = resolution.lineComments.size + resolution.unmappedIssues.size
        return ReviewOutput(
            body = reviewCommentFormatterPort.format(bodyReview, totalIssueCount, resolution.lineComments.size),
            lineComments = resolution.lineComments,
            hasNoIssues = review.issues.isEmpty(),
        )
    }

    // CancellationException은 재전파, 그 외 예외는 경고 로그 후 null 반환
    private suspend fun <T> runOrWarn(warnMessage: String, block: suspend () -> T): T? =
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { logger.warn(warnMessage, e); null }
}

// AgentException 서브타입을 메트릭/로그용 분류 문자열로 변환.
// when 이 exhaustive 라 sealed 에 신규 서브타입 추가 시 컴파일러가 매핑 누락을 잡아준다.
private fun AgentException.toFallbackReason(): String = when (this) {
    is AgentUnavailableException     -> "unavailable"
    is AgentAnalysisTimeoutException -> "timeout"
    is AgentAnalysisFailedException  -> "failed"
}
