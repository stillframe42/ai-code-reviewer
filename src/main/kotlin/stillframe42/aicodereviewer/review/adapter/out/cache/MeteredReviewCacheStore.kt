package stillframe42.aicodereviewer.review.adapter.out.cache

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.common.metrics.ReviewMetrics
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStore
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStatsStore

// ReviewCacheStore 데코레이터 — RedisReviewCacheAdapter를 감싸 hit/miss 메트릭과 로그를 더한다.
// 이로써 호출자(DefaultReviewService)는 캐시 조회 결과만 신경 쓰면 되고,
// 메트릭/로그 같은 cross-cutting concern 은 이 데코레이터가 단일 위치에서 관리한다.
//
// put 은 best-effort 로 동작한다 — 캐시 저장 실패는 리뷰 결과 반환에 영향을 주지 않는다.
@Component
class MeteredReviewCacheStore(
    private val delegate: RedisReviewCacheAdapter,
    private val reviewMetrics: ReviewMetrics,
    private val reviewCacheStatsStore: ReviewCacheStatsStore,
) : ReviewCacheStore, Logging {

    override suspend fun get(key: String): CodeReview? {
        val cached = delegate.get(key)
        if (cached != null) {
            logger.debug("캐시 히트: key={}", key)
            reviewMetrics.recordCacheHit()
            reviewCacheStatsStore.incrementHit()
        } else {
            logger.debug("캐시 미스: key={}", key)
            reviewMetrics.recordCacheMiss()
            reviewCacheStatsStore.incrementMiss()
        }
        return cached
    }

    override suspend fun put(key: String, value: CodeReview) {
        runCatching { delegate.put(key, value) }
            .onFailure { e -> logger.warn("캐시 저장 실패 (무시): {}", e.message) }
    }
}
