package stillframe42.aicodereviewer.github.application

import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
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
import stillframe42.aicodereviewer.review.domain.model.PrReviewCommand
import stillframe42.aicodereviewer.review.domain.port.`in`.PrReviewOrchestrationUseCase

// GitHub Webhook 유스케이스 구현 — PR 이벤트 수신 시 중복 처리·diff 조회·코멘트 등록을 담당하고,
// 리뷰 생성은 PrReviewOrchestrationUseCase 에 위임한다
@Service
class DefaultGitHubWebhookService(
    private val gitHubApiPort: GitHubApiPort,
    private val reviewCommentFormatterPort: ReviewCommentFormatterPort,
    private val processedEventPort: ProcessedEventPort,
    private val prReviewOrchestrationUseCase: PrReviewOrchestrationUseCase,
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

        val review = prReviewOrchestrationUseCase.orchestrate(event.toCommand(inputs))

        dismissPreviousReview(event)
        val newReviewId = submitReviewToGitHub(event, review, inputs.diff)

        if (review == null) return
        markProcessed(event, newReviewId)
    }

    private fun PullRequestEvent.toCommand(inputs: PrInputs) = PrReviewCommand(
        repositoryFullName = repositoryFullName,
        pullRequestNumber = pullRequestNumber,
        headSha = headSha,
        installationId = installationId,
        prDiff = inputs.diff,
        prFiles = inputs.files,
    )

    // 중복 처리 방지 — 동일 (레포, PR번호, SHA) 조합은 스킵
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

    // PR diff + 변경 파일 목록 조회. diff 가 비어 있으면 null 반환 (메타데이터만 변경된 PR)
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

    // 이전 리뷰 dismiss — 실패해도 새 리뷰 등록은 계속 진행
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

    // PR Reviews API 등록. 리뷰 실패 시 에러 안내 코멘트로 등록
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

    // 처리 완료 기록 (중복 방지) — review_id 포함하여 저장. 리뷰 실패 시 호출 안 함 (다음 이벤트에서 재처리 허용)
    private suspend fun markProcessed(event: PullRequestEvent, newReviewId: Long) {
        processedEventPort.markAsProcessed(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            headSha = event.headSha,
            reviewId = newReviewId,
        )
    }

    // diff position 매핑 + PR 코멘트 출력 구성
    private fun buildReviewOutput(review: CodeReview, prDiff: String): ReviewOutput {
        val resolution = DiffPositionResolver.resolve(prDiff, review.issues)
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
