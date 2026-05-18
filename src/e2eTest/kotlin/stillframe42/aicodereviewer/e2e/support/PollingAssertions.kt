package stillframe42.aicodereviewer.e2e.support

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import java.time.Duration

// AgentPoller 의 "polling done: id=..., attempts=N, elapsed=Mms" 로그를 파싱해
// attempts 와 avg interval 을 박제한다.
object PollingAssertions {

    // AgentPoller.kt 의 "polling done: id={}, attempts={}, elapsed={}ms" 로그 형식과 정합
    private val DONE_REGEX = Regex("""polling done: id=.+?, attempts=(\d+), elapsed=(\d+)ms""")

    // polling attempts 가 주어진 범위 안에서 발생 후 DONE 으로 종료되었는지 박제
    fun assertPollingHappened(logs: SpringBootLogTail, minAttempts: Int, maxAttempts: Int) {
        await atMost Duration.ofSeconds(60) untilAsserted {
            val parsed = findDoneLog(logs)
            assertThat(parsed)
                .withFailMessage(
                    "polling done 로그 미발견 (snapshot=%s)",
                    logs.snapshot().map { it.formattedMessage },
                )
                .isNotNull
            val attempts = parsed!!.first
            assertThat(attempts)
                .withFailMessage("polling attempts=%d 가 [%d, %d] 범위 밖", attempts, minAttempts, maxAttempts)
                .isBetween(minAttempts, maxAttempts)
        }
    }

    // avg interval = elapsed / attempts 가 expected ± toleranceFactor 이내인지 박제
    fun assertPollingIntervalRoughlyMatches(
        logs: SpringBootLogTail,
        expectedIntervalMs: Long,
        toleranceFactor: Double = 0.5,
    ) {
        val parsed = findDoneLog(logs)
            ?: throw AssertionError("polling done 로그 미발견 (interval 검증 불가)")
        val (attempts, elapsedMs) = parsed
        val avgInterval = elapsedMs.toDouble() / attempts
        val min = expectedIntervalMs * (1 - toleranceFactor)
        val max = expectedIntervalMs * (1 + toleranceFactor)
        assertThat(avgInterval)
            .withFailMessage(
                "avg interval=%.1fms 가 expected=%dms 의 ±%.0f%% 범위 [%.1fms, %.1fms] 밖",
                avgInterval, expectedIntervalMs, toleranceFactor * 100, min, max,
            )
            .isBetween(min, max)
    }

    private fun findDoneLog(logs: SpringBootLogTail): Pair<Int, Long>? = logs.snapshot()
        .firstNotNullOfOrNull { DONE_REGEX.find(it.formattedMessage)?.destructured }
        ?.let { (a, e) -> a.toInt() to e.toLong() }
}
