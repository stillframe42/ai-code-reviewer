package stillframe42.aicodereviewer.review.adapter.out.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStatsPort

// Redis INCR 기반 캐시 통계 어댑터
// stats:cache:hit, stats:cache:miss 키에 INCR / GET 연산만 수행한다. TTL 없음(누적 통계).
@Component
class RedisReviewCacheStatsAdapter(
    private val redisTemplate: StringRedisTemplate,
) : ReviewCacheStatsPort {

    companion object {
        private const val HIT_KEY = "stats:cache:hit"
        private const val MISS_KEY = "stats:cache:miss"
    }

    override fun incrementHit() {
        redisTemplate.opsForValue().increment(HIT_KEY)
    }

    override fun incrementMiss() {
        redisTemplate.opsForValue().increment(MISS_KEY)
    }

    override fun getHitCount(): Long =
        redisTemplate.opsForValue().get(HIT_KEY)?.toLongOrNull() ?: 0L

    override fun getMissCount(): Long =
        redisTemplate.opsForValue().get(MISS_KEY)?.toLongOrNull() ?: 0L
}
