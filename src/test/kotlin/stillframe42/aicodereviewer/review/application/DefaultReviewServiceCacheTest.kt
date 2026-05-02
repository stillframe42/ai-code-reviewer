package stillframe42.aicodereviewer.review.application

import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs
import stillframe42.aicodereviewer.review.domain.model.DiffFilterOptions
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import stillframe42.aicodereviewer.review.domain.port.out.ReviewCacheStatsStore

// DefaultReviewService 캐시 통합 테스트
// 동일 diff의 두 번째 리뷰 호출이 캐시에서 반환되어 AI 호출이 발생하지 않음을 검증한다.
class DefaultReviewServiceCacheTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reviewUseCase: ReviewUseCase

    @Autowired
    private lateinit var reviewCacheStatsStore: ReviewCacheStatsStore

    @Autowired
    private lateinit var aiReviewerProperties: AiReviewerProperties

    @BeforeEach
    fun setUpCacheTest() {
        WireMockStubs.stubAnthropicReviewWithIssues(wireMock)
        // RAG ConventionContextService가 diffOptions 존재 시 임베딩 API를 호출하므로 스텁 등록
        WireMockStubs.stubOpenAiEmbedding(wireMock)
    }

    @Test
    fun `동일 diff를 두 번 리뷰하면 두 번째는 캐시에서 반환되어 AI 호출이 발생하지 않는다`(): Unit = runBlocking {
        val diff = """
            diff --git a/src/MyService.kt b/src/MyService.kt
            --- a/src/MyService.kt
            +++ b/src/MyService.kt
            @@ -1,1 +1,2 @@
             class MyService
            +    // 캐시 테스트용 고유 변경
        """.trimIndent()

        val result1 = reviewUseCase.reviewCode(
            code = diff,
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
        )
        val result2 = reviewUseCase.reviewCode(
            code = diff,
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
        )

        // AI는 1회만 호출됨
        wireMock.verify(exactly(1), postRequestedFor(urlPathEqualTo("/v1/messages")))
        // 결과 동일
        assertThat(result2.summary).isEqualTo(result1.summary)
        assertThat(result2.overallScore).isEqualTo(result1.overallScore)
    }

    @Test
    fun `다른 diff는 캐시를 공유하지 않아 각각 AI를 호출한다`(): Unit = runBlocking {
        val diff1 = """
            diff --git a/src/FooService.kt b/src/FooService.kt
            +++ b/src/FooService.kt
            +class FooService
        """.trimIndent()
        val diff2 = """
            diff --git a/src/BarService.kt b/src/BarService.kt
            +++ b/src/BarService.kt
            +class BarService
        """.trimIndent()

        reviewUseCase.reviewCode(code = diff1, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())
        reviewUseCase.reviewCode(code = diff2, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())

        // 서로 다른 diff이므로 각각 AI 호출 → 총 2회
        wireMock.verify(exactly(2), postRequestedFor(urlPathEqualTo("/v1/messages")))
    }

    @Test
    fun `캐시 히트 시 임베딩 API는 호출되지 않는다`(): Unit = runBlocking {
        val diff = """
            diff --git a/src/main/kotlin/CacheMissOnly.kt b/src/main/kotlin/CacheMissOnly.kt
            --- a/src/main/kotlin/CacheMissOnly.kt
            +++ b/src/main/kotlin/CacheMissOnly.kt
            @@ -1,1 +1,2 @@
             class CacheMissOnly
            +    // RAG 캐시 미스 전용 검증
        """.trimIndent()

        // 첫 번째 호출 — 캐시 미스 → 임베딩 API 호출
        reviewUseCase.reviewCode(code = diff, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())
        // 두 번째 호출 — 캐시 히트 → 임베딩 API 미호출
        reviewUseCase.reviewCode(code = diff, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())

        // 임베딩 API는 캐시 미스 시 1회만 호출되어야 한다
        wireMock.verify(exactly(1), postRequestedFor(urlPathEqualTo("/v1/embeddings")))
    }

    @Test
    fun `캐시 키는 설정된 keyVersion prefix를 포함하여 저장된다`(): Unit = runBlocking {
        val diff = """
            diff --git a/src/main/kotlin/Versioned.kt b/src/main/kotlin/Versioned.kt
            --- a/src/main/kotlin/Versioned.kt
            +++ b/src/main/kotlin/Versioned.kt
            @@ -1,1 +1,2 @@
             class Versioned
            +    // keyVersion prefix 검증용 고유 변경
        """.trimIndent()

        reviewUseCase.reviewCode(
            code = diff,
            provider = AiProvider.ANTHROPIC,
            diffOptions = DiffFilterOptions(),
        )

        // 현재 설정된 keyVersion (기본 v1) prefix 를 그대로 사용한 키만 조회되어야 한다
        val expectedPrefix = "review:cache:${aiReviewerProperties.cache.keyVersion}:"
        val versionedKeys = redisTemplate.keys("$expectedPrefix*").collectList().awaitSingle()
        assertThat(versionedKeys)
            .hasSize(1)
            .allSatisfy { key -> assertThat(key).startsWith(expectedPrefix) }
    }

    @Test
    fun `캐시 히트 시 Redis hit 카운터와 miss 카운터가 각각 1씩 증가한다`(): Unit = runBlocking {
        val diff = """
            diff --git a/src/Counter.kt b/src/Counter.kt
            --- a/src/Counter.kt
            +++ b/src/Counter.kt
            @@ -1,1 +1,2 @@
             class Counter
            +    // Redis 카운터 테스트용 고유 변경
        """.trimIndent()

        // 첫 번째 호출 — 캐시 미스
        reviewUseCase.reviewCode(code = diff, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())
        // 두 번째 호출 — 캐시 히트
        reviewUseCase.reviewCode(code = diff, provider = AiProvider.ANTHROPIC, diffOptions = DiffFilterOptions())

        // AbstractIntegrationTest.setUpBase()의 FLUSHALL로 stats 카운터가 초기화됨을 전제로 절댓값 비교
        assertThat(reviewCacheStatsStore.getHitCount()).isEqualTo(1L)
        assertThat(reviewCacheStatsStore.getMissCount()).isEqualTo(1L)
    }
}
