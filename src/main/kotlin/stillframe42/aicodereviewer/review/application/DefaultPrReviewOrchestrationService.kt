package stillframe42.aicodereviewer.review.application

import kotlinx.coroutines.CancellationException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisFailedException
import stillframe42.aicodereviewer.agent.domain.exception.AgentAnalysisTimeoutException
import stillframe42.aicodereviewer.agent.domain.exception.AgentException
import stillframe42.aicodereviewer.agent.domain.exception.AgentUnavailableException
import stillframe42.aicodereviewer.agent.domain.model.AgentFallbackEvent
import stillframe42.aicodereviewer.agent.domain.port.`in`.AgentReviewUseCase
import stillframe42.aicodereviewer.agent.domain.service.SecurityFileDetector
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.model.PrReviewCommand
import stillframe42.aicodereviewer.review.domain.model.ReviewCompletedEvent
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.model.ReviewRequestStatus
import stillframe42.aicodereviewer.review.domain.port.`in`.PrReviewOrchestrationUseCase
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.ReviewPersistencePort
import java.time.Instant

// PR 리뷰 오케스트레이션 구현 — 영속 라이프사이클, AI 경로 선택/폴백, 완료 이벤트 발행을 책임진다
@Service
class DefaultPrReviewOrchestrationService(
    private val reviewUseCase: ReviewUseCase,
    private val agentReviewUseCase: AgentReviewUseCase,
    private val reviewPersistencePort: ReviewPersistencePort,
    private val eventPublisher: ApplicationEventPublisher,
) : PrReviewOrchestrationUseCase, Logging {

    override suspend fun orchestrate(command: PrReviewCommand): CodeReview? {
        val startNanos = System.nanoTime()
        val reviewRequestId = startPersistedReview(command)
        val review = runAiReview(command, reviewRequestId)
        finalizePersistedReview(reviewRequestId, review)
        publishCompletedEvent(command, review, startNanos)
        return review
    }

    // 리뷰 요청 PENDING 저장 후 PROCESSING 으로 전환. 저장 실패가 리뷰 흐름을 중단시키지 않도록 격리
    private suspend fun startPersistedReview(command: PrReviewCommand): Long? {
        val reviewRequestId = runOrWarn("리뷰 요청 저장 실패 (리뷰는 계속 진행)") {
            reviewPersistencePort.saveReviewRequest(
                repoFullName = command.repositoryFullName,
                prNumber = command.pullRequestNumber,
                headSha = command.headSha,
            )
        } ?: return null

        runOrWarn("리뷰 상태 업데이트 실패") {
            reviewPersistencePort.updateReviewStatus(reviewRequestId, ReviewRequestStatus.PROCESSING)
        }
        return reviewRequestId
    }

    // 리뷰 결과 저장 + 상태 종료. saveReviewResult 실패 시에도 상태 업데이트(DONE)가 반드시 실행되도록 블록을 분리한다
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

    // AI 리뷰 실행 — 보안 파일 포함 시 agent 경로 우선, AgentException 발생 시 Spring AI 경로로 폴백
    private suspend fun runAiReview(command: PrReviewCommand, reviewRequestId: Long?): CodeReview? {
        if (!SecurityFileDetector.hasSecurityFile(command.prFiles)) {
            return runDefaultReview(command)
        }
        return try {
            agentReviewUseCase.review(
                repositoryFullName = command.repositoryFullName,
                pullRequestNumber = command.pullRequestNumber,
                prDiff = command.prDiff,
                prFiles = command.prFiles,
                reviewRequestId = reviewRequestId,
            ).also {
                logger.info(
                    "Agent path 리뷰 완료: repo={}, pr={}, score={}",
                    command.repositoryFullName, command.pullRequestNumber, it.overallScore,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            fallbackToSpringAI(e.toFallbackReason(), command, e)
        } catch (e: Exception) {
            // AgentException 외 외부 시스템 transient 오류 (RAG 임베딩 실패 등) 도 동일하게 폴백.
            // "error" reason 으로 메트릭 분리 — 의도된 도메인 예외와 구분 가능.
            fallbackToSpringAI("error", command, e)
        }
    }

    // 분류된 reason 으로 이벤트/로그를 남기고 Spring AI 기본 리뷰 경로로 위임한다.
    // 메트릭 기록은 AgentMetricsEventListener 가 담당 — agent application 구체 클래스 직접 의존을 피한다
    private suspend fun fallbackToSpringAI(
        reason: String,
        command: PrReviewCommand,
        cause: Throwable,
    ): CodeReview? {
        eventPublisher.publishEvent(AgentFallbackEvent(reason))
        logger.warn(
            "Agent path 폴백: reason={}, repo={}, pr={}",
            reason, command.repositoryFullName, command.pullRequestNumber, cause,
        )
        // TODO(이벤트 큐 도입 후): cause is AgentAnalysisTimeoutException 일 때 scheduleRetry 분기 추가
        return runDefaultReview(command)
    }

    // Spring AI 기본 리뷰 경로 — agent 가 비활성화되거나 실패한 경우 사용
    private suspend fun runDefaultReview(command: PrReviewCommand): CodeReview? =
        try {
            reviewUseCase.reviewCode(
                code = command.prDiff,
                provider = AiProvider.ANTHROPIC,
                diffOptions = DiffFilterOptions(),
                mode = ReviewMode.WithGitHubTools(command.installationId),
            ).also { review ->
                logger.info(
                    "AI 리뷰 생성 완료: repo={}, pr={}, score={}",
                    command.repositoryFullName, command.pullRequestNumber, review.overallScore,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "리뷰 생성 실패: repo={}, pr={}",
                command.repositoryFullName, command.pullRequestNumber, e,
            )
            null
        }

    // 메트릭 기록은 ReviewMetricsEventListener 가 담당
    private fun publishCompletedEvent(command: PrReviewCommand, review: CodeReview?, startNanos: Long) {
        eventPublisher.publishEvent(
            ReviewCompletedEvent(
                repo = command.repositoryFullName,
                status = if (review != null) "DONE" else "FAILED",
                issues = review?.issues ?: emptyList(),
                durationNanos = System.nanoTime() - startNanos,
            )
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
