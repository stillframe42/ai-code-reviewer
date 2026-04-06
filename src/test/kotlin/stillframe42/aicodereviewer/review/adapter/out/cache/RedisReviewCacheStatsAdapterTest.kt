package stillframe42.aicodereviewer.review.adapter.out.cache

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStatsStore

// RedisReviewCacheStatsAdapter 통합 테스트 — Redis Testcontainers 사용
class RedisReviewCacheStatsAdapterTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reviewCacheStatsStore: ReviewCacheStatsStore

    @Test
    fun `incrementHit 호출 후 getHitCount는 1을 반환한다`() = runTest {
        reviewCacheStatsStore.incrementHit()
        assertThat(reviewCacheStatsStore.getHitCount()).isEqualTo(1L)
    }

    @Test
    fun `incrementMiss 호출 후 getMissCount는 1을 반환한다`() = runTest {
        reviewCacheStatsStore.incrementMiss()
        assertThat(reviewCacheStatsStore.getMissCount()).isEqualTo(1L)
    }

    @Test
    fun `데이터가 없을 때 getHitCount는 0을 반환한다`() = runTest {
        assertThat(reviewCacheStatsStore.getHitCount()).isEqualTo(0L)
    }

    @Test
    fun `데이터가 없을 때 getMissCount는 0을 반환한다`() = runTest {
        assertThat(reviewCacheStatsStore.getMissCount()).isEqualTo(0L)
    }

    @Test
    fun `incrementHit 3회 호출 후 getHitCount는 3을 반환한다`() = runTest {
        reviewCacheStatsStore.incrementHit()
        reviewCacheStatsStore.incrementHit()
        reviewCacheStatsStore.incrementHit()
        assertThat(reviewCacheStatsStore.getHitCount()).isEqualTo(3L)
    }
}
