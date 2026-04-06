package stillframe42.aicodereviewer.review.domain.port.out

// 캐시 hit/miss 카운터 아웃바운드 포트 — Redis INCR로 누적 통계를 관리한다
interface ReviewCacheStatsStore {
    suspend fun incrementHit()
    suspend fun incrementMiss()
    suspend fun getHitCount(): Long
    suspend fun getMissCount(): Long
}
