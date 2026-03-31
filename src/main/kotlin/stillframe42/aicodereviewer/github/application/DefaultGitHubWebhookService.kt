package stillframe42.aicodereviewer.github.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
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
import kotlinx.coroutines.CancellationException

// GitHub Webhook 유스케이스 구현 — PR 이벤트 수신 시 diff 조회 → AI 리뷰 → 코멘트 등록 흐름을 조율한다
@Service
class DefaultGitHubWebhookService(
    private val gitHubApiPort: GitHubApiPort,
    private val reviewUseCase: ReviewUseCase,
    private val reviewCommentFormatterPort: ReviewCommentFormatterPort,
    private val processedEventPort: ProcessedEventPort,
    private val diffPositionResolver: DiffPositionResolver,
    private val reviewPersistencePort: ReviewPersistencePort,
) : GitHubWebhookUseCase, Logging {

    // AI 리뷰 완료 후 PR에 등록할 준비가 된 결과물
    private data class ReviewOutput(
        val body: String,
        val lineComments: List<PrReviewLineComment>,
        val hasNoIssues: Boolean,
        val review: CodeReview,  // 저장용 원본 리뷰 결과
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

        // 1.5단계: 리뷰 요청 저장 (PENDING) — 저장 실패가 리뷰 흐름을 중단시키지 않도록 격리
        val reviewRequestId = runOrWarn("리뷰 요청 저장 실패 (리뷰는 계속 진행)") {
            reviewPersistencePort.saveReviewRequest(
                repoFullName = event.repositoryFullName,
                prNumber = event.pullRequestNumber,
                headSha = event.headSha,
            )
        }

        // 1.6단계: PROCESSING 상태 업데이트
        reviewRequestId?.let { id ->
            runOrWarn("리뷰 상태 업데이트 실패") {
                reviewPersistencePort.updateReviewStatus(id, ReviewRequestStatus.PROCESSING)
            }
        }

        // 2단계: AI 코드 리뷰 실행 (전처리 활성화)
        // 실패 시 null을 반환하고, 4단계에서 에러 코멘트를 등록한다
        val reviewOutput = generateReviewOutput(event, prDiff)

        // 2.5단계: 리뷰 결과 저장 — 성공: DONE + 결과, 실패: FAILED
        // saveReviewResult 실패 시에도 상태 업데이트(DONE)가 반드시 실행되도록 블록을 분리한다
        reviewRequestId?.let { id ->
            val now = Instant.now()
            if (reviewOutput != null) {
                runOrWarn("리뷰 결과 저장 실패") {
                    reviewPersistencePort.saveReviewResult(id, reviewOutput.review, null)
                }
                runOrWarn("리뷰 상태 DONE 업데이트 실패") {
                    reviewPersistencePort.updateReviewStatus(id, ReviewRequestStatus.DONE, now)
                }
            } else {
                runOrWarn("리뷰 상태 FAILED 업데이트 실패") {
                    reviewPersistencePort.updateReviewStatus(id, ReviewRequestStatus.FAILED, now)
                }
            }
        }

        // 3단계: 이전 리뷰 dismiss — 실패해도 새 리뷰 등록은 계속 진행
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

        // 4단계: PR Reviews API로 등록 — 이슈 유무에 따라 이벤트 타입 결정
        // 이슈 없음 → APPROVE, 이슈 있음 → REQUEST_CHANGES
        // 오류 발생 시 COMMENT 타입으로 에러 안내
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

        // 리뷰 실패 시 markAsProcessed 호출 안 함 — 다음 이벤트에서 재처리 허용
        if (reviewOutput == null) return

        // 5단계: 처리 완료 기록 (중복 방지) — review_id 포함하여 저장
        processedEventPort.markAsProcessed(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            headSha = event.headSha,
            reviewId = newReviewId,
        )
    }

    // AI 리뷰 실행 및 diff position 매핑 — 실패 시 null 반환하여 에러 코멘트 등록으로 이어진다
    private suspend fun generateReviewOutput(event: PullRequestEvent, prDiff: String): ReviewOutput? =
        try {
            val review = reviewUseCase.reviewCode(
                code = prDiff,
                provider = AiProvider.ANTHROPIC,
                diffOptions = DiffFilterOptions(),
                mode = ReviewMode.WithGitHubTools(event.installationId),
            )
            logger.info(
                "AI 리뷰 생성 완료: repo={}, pr={}, score={}",
                event.repositoryFullName, event.pullRequestNumber, review.overallScore,
            )

            // diff position 매핑 — 성공한 이슈는 인라인 코멘트, 실패한 이슈는 본문에 포함
            val resolution = diffPositionResolver.resolve(prDiff, review.issues)
            val bodyReview = review.copy(issues = resolution.unmappedIssues)
            ReviewOutput(
                body = reviewCommentFormatterPort.format(bodyReview),
                lineComments = resolution.lineComments,
                hasNoIssues = review.issues.isEmpty(),
                review = review,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("리뷰 생성 실패: repo={}, pr={}", event.repositoryFullName, event.pullRequestNumber, e)
            null
        }

    // CancellationException은 재전파, 그 외 예외는 경고 로그 후 null 반환
    private suspend fun <T> runOrWarn(warnMessage: String, block: suspend () -> T): T? =
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { logger.warn(warnMessage, e); null }
}
