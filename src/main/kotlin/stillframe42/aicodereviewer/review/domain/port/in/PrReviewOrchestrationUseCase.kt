package stillframe42.aicodereviewer.review.domain.port.`in`

import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.model.PrReviewCommand

// PR 리뷰 오케스트레이션 진입점 — 영속 라이프사이클 + AI 경로 선택/폴백 + 완료 이벤트 발행을 책임진다.
// 반환 null = 리뷰 생성 실패 (호출자가 에러 코멘트·재처리 정책을 결정)
interface PrReviewOrchestrationUseCase {
    suspend fun orchestrate(command: PrReviewCommand): CodeReview?
}
