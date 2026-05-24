package stillframe42.aicodereviewer.review.domain.port.out

import stillframe42.aicodereviewer.review.domain.model.CodeReview

// 코드 리뷰 결과 캐시 아웃바운드 포트 — 동일 diff 재호출 시 AI 호출 없이 캐시 결과 반환
interface ReviewCachePort {
    suspend fun get(key: String): CodeReview?
    suspend fun put(key: String, value: CodeReview)
}
