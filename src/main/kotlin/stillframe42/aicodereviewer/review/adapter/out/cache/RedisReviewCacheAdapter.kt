package stillframe42.aicodereviewer.review.adapter.out.cache

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.cache.AbstractRedisCacheAdapter
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.review.domain.model.CodeReview
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStore

// 코드 리뷰 결과 Redis 캐시 어댑터
// AbstractRedisCacheAdapter<CodeReview>를 상속하여 JSON 직렬화·TTL 처리를 위임한다.
@Component
class RedisReviewCacheAdapter(
    redisTemplate: ReactiveRedisTemplate<String, String>,
    properties: AiReviewerProperties,
) : AbstractRedisCacheAdapter<CodeReview>(
    redisTemplate,
    ObjectMapper(),
    CodeReview::class.java,
    properties.cache.ttl,
),
    ReviewCacheStore
