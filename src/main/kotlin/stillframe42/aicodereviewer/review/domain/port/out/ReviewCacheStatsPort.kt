package stillframe42.aicodereviewer.review.domain.port.out

// 캐시 hit/miss 누적 카운터 아웃바운드 포트 — 동일 diff 캐시 적중/미적중 횟수를 추적한다
interface ReviewCacheStatsPort {
    fun incrementHit()
    fun incrementMiss()
    fun getHitCount(): Long
    fun getMissCount(): Long
}
