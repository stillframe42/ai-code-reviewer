package stillframe42.aicodereviewer.review.adapter.out.cache

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.cache.AbstractRedisCacheAdapter
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.review.domain.model.CodeReview

// 코드 리뷰 결과 Redis 캐시 어댑터
// AbstractRedisCacheAdapter<CodeReview>를 상속하여 JSON 직렬화·TTL 처리를 위임한다.
// ReviewCachePort 포트는 MeteredReviewCacheAdapter가 이 어댑터를 감싸 메트릭/로그를 더한 형태로 제공한다.
@Component
class RedisReviewCacheAdapter(
    redisTemplate: ReactiveRedisTemplate<String, String>,
    objectMapper: ObjectMapper,
    properties: AiReviewerProperties,
) : AbstractRedisCacheAdapter<CodeReview>(
    redisTemplate,
    objectMapper,
    CodeReview::class.java,
    properties.cache.ttl,
)
