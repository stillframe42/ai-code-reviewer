package stillframe42.aicodereviewer.github.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.github.domain.model.PullRequestEvent
import stillframe42.aicodereviewer.github.domain.port.`in`.GitHubWebhookUseCase
import stillframe42.aicodereviewer.github.domain.port.out.GitHubApiPort
import stillframe42.aicodereviewer.github.domain.port.out.ProcessedEventPort
import stillframe42.aicodereviewer.github.domain.port.out.ReviewCommentFormatterPort
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

// GitHub Webhook 유스케이스 구현 — PR 이벤트 수신 시 diff 조회 → AI 리뷰 → 코멘트 등록 흐름을 조율한다
@Service
class DefaultGitHubWebhookService(
    private val gitHubApiPort: GitHubApiPort,
    private val reviewUseCase: ReviewUseCase,
    private val reviewCommentFormatterPort: ReviewCommentFormatterPort,
    private val processedEventPort: ProcessedEventPort,
) : GitHubWebhookUseCase {

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

        // 2단계: AI 코드 리뷰 실행 (전처리 활성화)
        // 실패 시 null을 반환하고, 3단계에서 에러 코멘트를 등록한다
        val reviewComment = try {
            val review = reviewUseCase.reviewCode(
                code = prDiff,
                provider = AiProvider.ANTHROPIC,
                diffOptions = DiffFilterOptions(),
            )
            logger.info(
                "AI 리뷰 생성 완료: repo={}, pr={}, score={}",
                event.repositoryFullName, event.pullRequestNumber, review.overallScore,
            )
            reviewCommentFormatterPort.format(review)
        } catch (e: Exception) {
            logger.error("리뷰 생성 실패: repo={}, pr={}", event.repositoryFullName, event.pullRequestNumber, e)
            null
        }

        // 3단계: PR 코멘트 등록 — 성공 시 리뷰, 실패 시 에러 안내
        gitHubApiPort.postReviewComment(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            comment = reviewComment ?: "⚠️ 코드 리뷰 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
            installationId = event.installationId,
        )

        // 리뷰 실패 시 markAsProcessed 호출 안 함 — 다음 이벤트에서 재처리 허용
        if (reviewComment == null) return

        // 4단계: 처리 완료 기록 (중복 방지) — 성공 시에만 기록
        processedEventPort.markAsProcessed(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            headSha = event.headSha,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultGitHubWebhookService::class.java)
    }
}
