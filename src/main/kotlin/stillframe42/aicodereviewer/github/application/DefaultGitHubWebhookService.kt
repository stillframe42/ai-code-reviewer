package stillframe42.aicodereviewer.github.application

import kotlinx.coroutines.CancellationException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.metrics.event.ReviewCompletedEvent
import stillframe42.aicodereviewer.core.AiProvider
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
) : GitHubWebhookUseCase, Logging {

    // diff position 매핑 + 포맷팅이 완료된 PR 코멘트 구성용 출력
    private data class ReviewOutput(
        val body: String,
        val lineComments: List<PrReviewLineComment>,
        val hasNoIssues: Boolean,
    )

    override suspend fun handlePullRequestEvent(event: PullRequestEvent) {
        logger.info(
            "PR 이벤트 처리 시작: repo={}, pr={}, action={}",
            event.repositoryFullName, event.pullRequestNumber, event.action,
        )

        // 0단계: 중복 처리 방지 — 동일 (레포, PR번호, SHA) 조합은 스킵
        if (processedEventPort.isAlreadyProcessed(
                repositoryFullName = event.repositoryFullName,
                pullRequestNumber = event.pullRequestNumber,
                headSha = event.headSha,
            )
        ) {
            logger.info(
                "이미 처리된 이벤트, 스킵: repo={}, pr={}, sha={}",
                event.repositoryFullName, event.pullRequestNumber, event.headSha,
            )
            return
        }

        // 1단계: PR diff 및 변경 파일 목록 조회
        val prDiff = gitHubApiPort.getPrDiff(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            installationId = event.installationId,
        )

        // diff가 비어있으면 리뷰 불가 — 코드 변경이 없는 PR (예: 메타데이터만 변경)
        if (prDiff.isBlank()) {
            logger.warn(
                "PR diff가 비어 있어 리뷰를 건너뜁니다: repo={}, pr={}",
                event.repositoryFullName, event.pullRequestNumber,
            )
            return
        }

        // diff 확인 후 시작 시각 기록 — 의미 있는 리뷰 플로우 전체 시간을 측정한다
        val startNanos = System.nanoTime()

        // 2단계: 리뷰 요청 저장 (PENDING) — 저장 실패가 리뷰 흐름을 중단시키지 않도록 격리
        val reviewRequestId = runOrWarn("리뷰 요청 저장 실패 (리뷰는 계속 진행)") {
            reviewPersistencePort.saveReviewRequest(
                repoFullName = event.repositoryFullName,
                prNumber = event.pullRequestNumber,
                headSha = event.headSha,
            )
        }

        // 3단계: PROCESSING 상태 업데이트
        reviewRequestId?.let { id ->
            runOrWarn("리뷰 상태 업데이트 실패") {
                reviewPersistencePort.updateReviewStatus(id, ReviewRequestStatus.PROCESSING)
            }
        }

        // 4단계: AI 코드 리뷰 실행 — 실패 시 null 반환, 4단계에서 에러 코멘트 등록
        val review = runAiReview(event, prDiff)

        // 5단계: 리뷰 결과 저장 — 성공: DONE + 결과, 실패: FAILED
        // saveReviewResult 실패 시에도 상태 업데이트(DONE)가 반드시 실행되도록 블록을 분리한다
        reviewRequestId?.let { id ->
            val now = Instant.now()
            val status = if (review != null) {
                runOrWarn("리뷰 결과 저장 실패") { reviewPersistencePort.saveReviewResult(id, review, review.modelName) }
                ReviewRequestStatus.DONE
            } else {
                ReviewRequestStatus.FAILED
            }
            runOrWarn("리뷰 상태 $status 업데이트 실패") {
                reviewPersistencePort.updateReviewStatus(id, status, now)
            }
        }

        // 6단계: 이전 리뷰 dismiss — 실패해도 새 리뷰 등록은 계속 진행
        processedEventPort.findLatestReviewId(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
        )?.let { previousReviewId ->
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

        // 7단계: diff position 매핑 + 출력 구성
        val reviewOutput = review?.let { buildReviewOutput(it, prDiff) }

        // 8단계: PR Reviews API로 등록 — 이슈 유무에 따라 이벤트 타입 결정
        val newReviewId = gitHubApiPort.postPrReview(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            review = if (reviewOutput != null) {
                PrReview(
                    body = reviewOutput.body,
                    event = if (reviewOutput.hasNoIssues) PrReviewEvent.APPROVE else PrReviewEvent.REQUEST_CHANGES,
                    lineComments = reviewOutput.lineComments,
                    commitId = event.headSha,
                )
            } else {
                PrReview(body = "⚠️ 코드 리뷰 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
            },
            installationId = event.installationId,
        )

        // 리뷰 완료 이벤트 발행 — 메트릭 기록은 MetricsEventListener가 담당
        val status = if (review != null) "DONE" else "FAILED"
        eventPublisher.publishEvent(
            ReviewCompletedEvent(
                repo = event.repositoryFullName,
                status = status,
                issues = review?.issues ?: emptyList(),
                durationNanos = System.nanoTime() - startNanos,
            )
        )

        // 리뷰 실패 시 markAsProcessed 호출 안 함 — 다음 이벤트에서 재처리 허용
        if (review == null) return

        // 9단계: 처리 완료 기록 (중복 방지) — review_id 포함하여 저장
        processedEventPort.markAsProcessed(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            headSha = event.headSha,
            reviewId = newReviewId,
        )
    }

    // AI 리뷰 실행 — 실패 시 null 반환, 에러 코멘트 등록으로 이어진다
    private suspend fun runAiReview(event: PullRequestEvent, prDiff: String): CodeReview? =
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
