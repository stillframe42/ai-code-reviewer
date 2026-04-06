package stillframe42.aicodereviewer.common.cache

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate

// 전체 프로젝트 공통 Redis 캐시 추상 클래스
// 새 기능에서 캐시가 필요하면 이 클래스를 상속하고 valueType과 ttl만 지정한다.
//
// 사용 예시:
//   class RedisXxxCacheAdapter(...) :
//       AbstractRedisCacheAdapter<XxxResult>(redisTemplate, objectMapper, XxxResult::class.java, ttl),
//       XxxCacheStore
abstract class AbstractRedisCacheAdapter<V : Any>(
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val valueType: Class<V>,
    private val ttl: Duration,
) {
    // Redis에서 JSON 문자열 조회 → 역직렬화. 키가 없으면 null 반환.
    suspend fun get(key: String): V? =
        redisTemplate.opsForValue()
            .get(key)
            .awaitSingleOrNull()
            ?.let { json -> objectMapper.readValue(json, valueType) }

    // value를 JSON 직렬화 후 TTL과 함께 Redis에 저장
    suspend fun put(key: String, value: V) {
        val json = objectMapper.writeValueAsString(value)
        redisTemplate.opsForValue()
            .set(key, json, ttl)
            .awaitSingle()
    }
}
