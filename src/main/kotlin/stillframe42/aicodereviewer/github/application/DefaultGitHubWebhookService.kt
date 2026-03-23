package stillframe42.aicodereviewer.github.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.github.domain.model.PullRequestEvent
import stillframe42.aicodereviewer.github.domain.model.ReviewComment
import stillframe42.aicodereviewer.github.domain.port.`in`.GitHubWebhookUseCase
import stillframe42.aicodereviewer.github.domain.port.out.GitHubApiPort
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase

// GitHub Webhook 유스케이스 구현 — PR 이벤트 수신 시 diff 조회 → AI 리뷰 → 코멘트 등록 흐름을 조율한다
@Service
class DefaultGitHubWebhookService(
    private val gitHubApiPort: GitHubApiPort,
    private val reviewUseCase: ReviewUseCase,
) : GitHubWebhookUseCase {

    private val logger = LoggerFactory.getLogger(DefaultGitHubWebhookService::class.java)

    override suspend fun handlePullRequestEvent(event: PullRequestEvent) {
        logger.info(
            "PR 이벤트 처리 시작: repo={}, pr={}, action={}",
            event.repositoryFullName, event.pullRequestNumber, event.action,
        )

        // 1단계: PR diff 조회
        val prDiff = gitHubApiPort.getPrDiff(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            installationId = event.installationId,
        )

        // diff가 비어있으면 리뷰 불가 — 코드 변경이 없는 PR (예: 메타데이터만 변경)
        if (prDiff.content.isBlank()) {
            logger.warn(
                "PR diff가 비어 있어 리뷰를 건너뜁니다: repo={}, pr={}",
                event.repositoryFullName, event.pullRequestNumber,
            )
            return
        }

        // 2단계: AI 코드 리뷰 실행
        val review = reviewUseCase.reviewCode(
            code = prDiff.content,
            provider = AiProvider.ANTHROPIC,
        )

        // 3단계: 마크다운 포맷 변환 후 PR 코멘트 등록
        val markdownBody = formatReviewComment(review)
        gitHubApiPort.postReviewComment(
            repositoryFullName = event.repositoryFullName,
            pullRequestNumber = event.pullRequestNumber,
            comment = ReviewComment(body = markdownBody),
            installationId = event.installationId,
        )

        logger.info(
            "PR 리뷰 코멘트 등록 완료: repo={}, pr={}, score={}",
            event.repositoryFullName, event.pullRequestNumber, review.overall_score,
        )
    }

    // CodeReview 결과를 GitHub PR 코멘트용 마크다운 문자열로 변환한다
    internal fun formatReviewComment(review: CodeReview): String = buildString {
        appendLine("## AI 코드 리뷰 결과")
        appendLine()
        appendLine("**종합 점수: ${review.overall_score}/10**")
        appendLine()
        appendLine("### 요약")
        appendLine(review.summary)
        appendLine()
        appendLine("---")
        appendLine()

        // 이슈 목록 섹션
        appendLine("### 이슈 목록 (${review.issues.size}건)")
        appendLine()
        if (review.issues.isEmpty()) {
            appendLine("발견된 이슈가 없습니다.")
        } else {
            review.issues.forEach { issue ->
                appendLine("#### [${issue.severity}] ${issue.category} — ${issue.description}")
                if (issue.line != null) {
                    appendLine("- **라인**: ${issue.line}번째 줄")
                }
                appendLine("- **제안**: ${issue.suggestion}")
                appendLine()
            }
        }

        appendLine("---")
        appendLine()

        // 잘한 점 섹션 — 항목이 없으면 섹션 전체 생략
        if (review.positives.isNotEmpty()) {
            appendLine("### 잘한 점")
            review.positives.forEach { positive ->
                appendLine("- $positive")
            }
            appendLine()
            appendLine("---")
            appendLine()
        }

        append("*이 리뷰는 AI가 자동으로 생성했습니다.*")
    }
}
